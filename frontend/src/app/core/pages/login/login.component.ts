import { Component, DestroyRef, ElementRef, PLATFORM_ID, ViewChild, afterNextRender, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthenticatedUser } from '../../dtos/auth.dto';
import { ApiError } from '../../dtos/api-error.dto';
import { AppRoute } from '../../enums/app-route.enum';
import { AuthService } from '../../services/auth.service';
import { GoogleIdentityService } from '../../services/google-identity.service';
import { StorageKey } from '../../enums/storage-key.enum';
import { isPlatformBrowser } from '@angular/common';

// Fallback when the server does not send Retry-After.
const LOCKOUT_FALLBACK_SECONDS = 5 * 60;

interface StoredLockout {
  username: string;
  until: number;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly googleIdentity = inject(GoogleIdentityService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);

  @ViewChild('googleButton') private googleButton?: ElementRef<HTMLElement>;

  readonly submitting = signal(false);
  readonly passwordVisible = signal(false);
  readonly failureMessage = signal<string | null>(null);
  readonly googleEnabled = this.googleIdentity.isEnabled;

  /** Seconds remaining until the lockout expires. 0 means not locked. */
  readonly lockoutRemaining = signal(0);

  private lockoutTimer: ReturnType<typeof setInterval> | null = null;

  readonly form = this.formBuilder.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  constructor() {
    afterNextRender(() => {
      const host = this.googleButton?.nativeElement;
      if (host) {
        void this.googleIdentity.renderButton(
          host,
          (idToken) => void this.signInWithGoogle(idToken),
        );
      }
      this.restoreLockout();
    });

    this.form.controls.username.valueChanges.subscribe(() => this.restoreLockout());
    this.destroyRef.onDestroy(() => this.clearLockoutTimer());
  }

  async submit(): Promise<void> {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.clearLockoutTimer();
    this.lockoutRemaining.set(0);
    this.clearStoredLockout();

    this.submitting.set(true);
    this.failureMessage.set(null);
    try {
      const user = await firstValueFrom(this.auth.signIn(this.form.getRawValue()));
      await this.routeAfterSignIn(user);
    } catch (error) {
      this.handleLoginError(error);
    } finally {
      this.submitting.set(false);
    }
  }

  private async signInWithGoogle(idToken: string): Promise<void> {
    this.submitting.set(true);
    this.failureMessage.set(null);
    try {
      const user = await firstValueFrom(this.auth.signInWithGoogle(idToken));
      await this.routeAfterSignIn(user);
    } catch {
      this.failureMessage.set('Google sign in failed. Please try again.');
    } finally {
      this.submitting.set(false);
    }
  }

  // A temporary password grants a token but no access until it is replaced.
  private routeAfterSignIn(user: AuthenticatedUser): Promise<boolean> {
    const target = user.mustChangePassword ? AppRoute.ChangePassword : AppRoute.Home;
    return this.router.navigate([target]);
  }

  // ---------- Lockout handling ----------

  private handleLoginError(error: unknown): void {
    const code = this.extractErrorCode(error);

    if (code === 'ACCOUNT_LOCKED') {
      const seconds = this.readRetryAfter(error) ?? LOCKOUT_FALLBACK_SECONDS;
      this.persistLockout(this.form.controls.username.value, seconds);
      this.startLockoutCountdown(seconds);
      return;
    }

    this.failureMessage.set('Sign in failed. Please check your credentials and try again.');
  }

  private readRetryAfter(error: unknown): number | null {
    if (!(error instanceof HttpErrorResponse)) {
      return null;
    }
    const seconds = Number(error.headers.get('Retry-After'));
    return Number.isFinite(seconds) && seconds > 0 ? seconds : null;
  }

  private extractErrorCode(error: unknown): string | null {
    if (error instanceof HttpErrorResponse) {
      return (error.error as ApiError | null)?.code ?? null;
    }
    return null;
  }

  private startLockoutCountdown(totalSeconds: number): void {
    this.clearLockoutTimer();

    this.lockoutRemaining.set(Math.ceil(totalSeconds));
    this.updateLockoutMessage();

    this.lockoutTimer = setInterval(() => {
      const remaining = this.lockoutRemaining() - 1;
      if (remaining <= 0) {
        this.lockoutRemaining.set(0);
        this.failureMessage.set(null);
        this.clearLockoutTimer();
        this.clearStoredLockout();
      } else {
        this.lockoutRemaining.set(remaining);
        this.updateLockoutMessage();
      }
    }, 1000);
  }

  private updateLockoutMessage(): void {
    const remaining = this.lockoutRemaining();
    const minutes = Math.floor(remaining / 60);
    const seconds = remaining % 60;
    const time = minutes > 0
      ? `${minutes}:${seconds.toString().padStart(2, '0')}`
      : `${seconds}s`;
    this.failureMessage.set(
      `Account is temporarily locked due to too many failed attempts. Try again in ${time}.`,
    );
  }

  private clearLockoutTimer(): void {
    if (this.lockoutTimer !== null) {
      clearInterval(this.lockoutTimer);
      this.lockoutTimer = null;
    }
  }

  private persistLockout(username: string, seconds: number): void {
    if (!this.isBrowser) {
      return;
    }
    const entry: StoredLockout = {
      username: username.trim().toLowerCase(),
      until: Date.now() + seconds * 1000,
    };
    localStorage.setItem(StorageKey.Lockout, JSON.stringify(entry));
  }

  /** Re-arms the countdown when the typed username matches the account that was locked. */
  private restoreLockout(): void {
    if (!this.isBrowser) {
      return;
    }
    const raw = localStorage.getItem(StorageKey.Lockout);
    if (!raw) {
      return;
    }

    let entry: StoredLockout;
    try {
      entry = JSON.parse(raw) as StoredLockout;
    } catch {
      this.clearStoredLockout();
      return;
    }

    const remainingMs = entry.until - Date.now();
    if (remainingMs <= 0) {
      this.clearStoredLockout();
      return;
    }
    if (entry.username !== this.form.controls.username.value.trim().toLowerCase()) {
      return;
    }
    if (this.lockoutRemaining() === 0) {
      this.startLockoutCountdown(remainingMs / 1000);
    }
  }

  private clearStoredLockout(): void {
    if (this.isBrowser) {
      localStorage.removeItem(StorageKey.Lockout);
    }
  }

  private get isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }
}

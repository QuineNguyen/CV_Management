import { Injectable, computed, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { finalize, map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CurrentUser } from '../models/user.model';
import { AuthenticatedUser, ChangePasswordRequest, LoginRequest, LoginResponse, ResetPasswordResponse } from '../dtos/auth.dto';
import { GoogleIdentityService } from './google-identity.service';
import { ApiEndpoint } from '../enums/api-endpoint.enum';
import { StorageKey } from '../enums/storage-key.enum';
import { UserRole } from '../enums/user-role.enum';

export type { CurrentUser };

/** Manages user session state, authentication token, and current user profile. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly googleIdentity = inject(GoogleIdentityService);

  private readonly tokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<AuthenticatedUser | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);
  readonly mustChangePassword = computed(() => this.userSignal()?.mustChangePassword ?? false);

  constructor() {
    this.restoreSession();
  }

  // ---------- Sign-in ----------

  signIn(request: LoginRequest): Observable<AuthenticatedUser> {
    return this.http.post<LoginResponse>(this.url(ApiEndpoint.Login), request).pipe(
      map(response => this.applySession(response))
    );
  }

  signInWithGoogle(idToken: string): Observable<AuthenticatedUser> {
    return this.http.post<LoginResponse>(this.url(ApiEndpoint.GoogleLogin), { idToken }).pipe(
      map(response => this.applySession(response))
    );
  }

  // ---------- Sign-out ----------
  /*
   * Revokes the token server-side, then clears the client.
   * The local session is cleared even if the request fails, so a network error
   * cannot leave the browser holding a token the user believes is gone.
   */
  signOut(): Observable<void> {
    return this.http.post<void>(this.url(ApiEndpoint.Logout), {}).pipe(
      finalize(() => {
        this.clearSession();
      })
    );
  }

  /** Clears local session state without invoking server logout endpoint. */
  clearSession(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    this.googleIdentity.disableAutoSelect();
    if (this.isBrowser) {
      localStorage.removeItem(StorageKey.Token);
      localStorage.removeItem(StorageKey.User);
    }
  }

  // ---------- Password ----------
  // All sessions are revoked server-side, so the caller must force a re-login.
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(this.url(ApiEndpoint.ChangePassword), request);
  }

  resetPassword(id: string): Observable<ResetPasswordResponse> {
    return this.http.post<ResetPasswordResponse>(this.url(`${ApiEndpoint.ResetPassword}/${id}/reset-password`), null);
  }

  // ---------- Session persistence ----------
  restoreSession(): void {
    if (!this.isBrowser) {
      return;
    }
    const token = localStorage.getItem(StorageKey.Token);
    const rawUser = localStorage.getItem(StorageKey.User);
    if (!token || !rawUser) {
      return;
    }
    try {
      this.tokenSignal.set(token);
      this.userSignal.set(JSON.parse(rawUser) as AuthenticatedUser);
    } catch {
      // Reset session if local data is corrupted.
      this.clearSession();
    }
  }

  hasRole(...roles: UserRole[]): boolean {
    const role = this.userSignal()?.role;
    return role !== undefined && roles.includes(role);
  }

  // ---------- Helpers ----------

  private applySession(response: LoginResponse): AuthenticatedUser {
    this.tokenSignal.set(response.token);
    this.userSignal.set(response.user);

    if (this.isBrowser) {
      localStorage.setItem(StorageKey.Token, response.token);
      localStorage.setItem(StorageKey.User, JSON.stringify(response.user));
    }
    return response.user;
  }

  private get isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }

  private url(endpoint: string): string {
    return `${environment.apiBaseUrl}${endpoint}`;
  }
}
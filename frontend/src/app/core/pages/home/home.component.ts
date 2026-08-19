import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../services/auth.service';
import { environment } from '../../../../environments/environment';
import { Language } from '../../models/language.model';

interface HealthResponse {
  status: string;
  serverTime: string;
  jvmTimeZone: string;
  dbTimeZone: string;
  dbCharset: string;
  dbCollation: string;
  schemaVersion: string;
}

interface RoundTripResponse {
  id: number;
  reasonSent: string;
  reasonReadBack: string;
  textMatches: boolean;
  deadlineReadBack: string;
  deadlineInLocalTime: boolean;
  jvmTimeZone: string;
}

interface ImageUploadResponse {
  objectKey: string;
  presignedUrl: string;
  signedWithPublicEndpoint: boolean;
}

/** Home screen and initial environment health check panel. */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    FormsModule,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly user = this.auth.user;

  readonly health = signal<HealthResponse | null>(null);
  readonly loadingHealth = signal(false);

  readonly roundTrip = signal<RoundTripResponse | null>(null);
  readonly busyRoundTrip = signal(false);
  readonly upload = signal<ImageUploadResponse | null>(null);

  /** Test string containing Japanese, Vietnamese diacritics, and 4-byte emoji. */
  sampleText = 'ソフトウェア開発者 — Kỹ sư phần mềm 🎌';
  sampleLanguage: Language = 'JA';
  deadlineDate: Date = new Date();

  constructor() {
    void this.loadHealth();
  }

  async loadHealth(): Promise<void> {
    this.loadingHealth.set(true);
    try {
      this.health.set(
        await firstValueFrom(this.http.get<HealthResponse>(`${environment.apiBaseUrl}/health`)),
      );
    } finally {
      this.loadingHealth.set(false);
    }
  }

  async runRoundTrip(): Promise<void> {
    this.busyRoundTrip.set(true);
    try {
      // Send date only; server calculates the end-of-day timestamp.
      const body = {
        reason: this.sampleText,
        deadlineDate: this.toIsoDate(this.deadlineDate),
        language: this.sampleLanguage,
      };
      this.roundTrip.set(
        await firstValueFrom(
          this.http.post<RoundTripResponse>(`${environment.apiBaseUrl}/smoke/round-trip`, body),
        ),
      );
    } finally {
      this.busyRoundTrip.set(false);
    }
  }

  async clearRoundTrip(): Promise<void> {
    await firstValueFrom(this.http.delete(`${environment.apiBaseUrl}/smoke/round-trip`));
    this.roundTrip.set(null);
  }

  async uploadImage(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const form = new FormData();
    form.append('file', file);
    this.upload.set(
      await firstValueFrom(
        this.http.post<ImageUploadResponse>(`${environment.apiBaseUrl}/smoke/image`, form),
      ),
    );
    input.value = '';
  }

  /** Formats local date to YYYY-MM-DD string without UTC conversion. */
  private toIsoDate(date: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }
}
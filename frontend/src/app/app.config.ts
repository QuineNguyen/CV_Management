import {
  ApplicationConfig,
  provideZoneChangeDetection,
  provideAppInitializer,
  inject,
  LOCALE_ID,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeVi from '@angular/common/locales/vi';
import { MAT_DATE_LOCALE } from '@angular/material/core';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { AuthService } from './core/services/auth.service';

registerLocaleData(localeVi);

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),

    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),

    // Interceptors executed in order for requests and reverse order for responses.
    provideHttpClient(
      withInterceptors([authInterceptor, errorInterceptor]),
    ),

    { provide: LOCALE_ID, useValue: 'vi' },
    { provide: MAT_DATE_LOCALE, useValue: 'vi-VN' },

    // Restore user session before route guards run on application startup.
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ],
};
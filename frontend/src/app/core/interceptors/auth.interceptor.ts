import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/** Endpoint URL prefixes that do not require an Authorization header. */
const NO_TOKEN_PREFIXES = ['/api/auth/login', '/api/health', '/storage/'];

/** Attaches Bearer authentication token to outgoing HTTP requests. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token();

  const skip = !token || NO_TOKEN_PREFIXES.some((prefix) => req.url.startsWith(prefix));
  if (skip) {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
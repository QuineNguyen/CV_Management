import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ERROR_MESSAGES, messageFor } from '../models/error-messages.model';
import { ApiError } from '../dtos/api-error.dto';
import { ToastService } from '../services/toast.service';
import { ApiEndpoint } from '../enums/api-endpoint.enum';
import { AppRoute } from '../enums/app-route.enum';

/**
 * Turns every failed request into one visible, consistent outcome.
 *
 * <p>The rule this enforces: a screen only handles a failure itself when it can do something
 * specific about it — highlight a field, roll back an optimistic edit, offer a reload. Everything
 * else is announced here once. Without a central handler, each screen grows its own half of the
 * error matrix and the halves drift.
 *
 * <p>Per status:
 * <ul>
 *   <li><b>401</b> - the session is gone or was revoked server-side. Clear local state without
 *       calling sign-out (that call would only produce a second 401) and send the user to the
 *       sign-in page, remembering where they were.</li>
 *   <li><b>403</b> - out of data scope. Nothing the user can do, so a notice is the whole
 *       response; the error is not re-thrown as something screens should handle.</li>
 *   <li><b>409</b> - someone else acted first. The user needs to reload, so say exactly that.</li>
 *   <li><b>422</b> - a business rule was broken. Shown here, and re-thrown so a form can also
 *       react (for example by keeping the submit button enabled).</li>
 *   <li><b>400</b> - re-thrown without a notice when it carries field errors, because the form
 *       renders those inline and a snackbar on top would be duplicate noise.</li>
 * </ul>
 *
 * <p>The error is always re-thrown. Swallowing it would leave a caller's loading spinner running
 * forever, since neither `next` nor `error` would ever arrive.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const auth = inject(AuthService);
  const router = inject(Router);

  const notify = (message: string) => toast.error(message);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Handle network errors (status 0).
      if (error.status === 0) {
        notify(ERROR_MESSAGES.NETWORK_ERROR);
        return throwError(() => error);
      }

      const body = error.error as ApiError | null;
      const code = body?.code ?? '';
      const message = messageFor(code, body?.message);

      switch (error.status) {
        case 401:
          auth.clearSession();
          // Skip redirect when the request is a login/auth call (the login
          // component handles its own errors) or the user is already on the
          // login page — otherwise returnUrl keeps nesting on every attempt.
          const isSignInAttempt = req.url.includes(ApiEndpoint.Login)
              || req.url.includes(ApiEndpoint.GoogleLogin);

          if (!isSignInAttempt && !router.url.startsWith(AppRoute.Login)) {
            notify(message);
            void router.navigate(['/login'], {
              queryParams: { returnUrl: router.url },
            });
          }
          break;

        case 403:
          notify(message);
          break;

        case 404:
          // Only notify for GET requests.
          if (req.method === 'GET') {
            notify(message);
          }
          break;

        case 409:
          toast.error(message, 'Reload')
            .onAction()
            .subscribe(() => window.location.reload());
          break;

        case 422:
          notify(message);
          break;

        case 400:
          // Skip notification if response has field-level validation errors.
          if (!body?.fieldErrors?.length) {
            notify(message);
          }
          break;

        default:
          notify(messageFor('INTERNAL_ERROR'));
      }

      return throwError(() => error);
    }),
  );
};
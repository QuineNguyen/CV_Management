import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { UserRole } from '../enums/user-role.enum';
import { AuthService } from './auth.service';
import { AppRoute } from '../enums/app-route.enum';
import { QueryParam } from '../enums/query-param.enum';

/**
 * Blocks routes that require a session, remembering where the user was heading so sign-in can
 * return them there instead of dropping them on the home page.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree([AppRoute.Login], { queryParams: { [QueryParam.ReturnUrl]: state.url } });
};

/**
 * Restricts a route to specific roles.
 *
 * <p>This is a navigation convenience, not a security boundary: the server enforces the same rule
 * and answers 403 regardless of what the client believes. Its job is to avoid showing a user a
 * screen that would only fail once it loaded.
 */
export function roleGuard(...allowed: UserRole[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.hasRole(...allowed) ? true : router.createUrlTree([AppRoute.Home]);
  };
}
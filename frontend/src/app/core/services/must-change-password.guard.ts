import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { AuthService } from "./auth.service";
import { AppRoute } from "../enums/app-route.enum";

/*
 * Blocks every in-app route while the account still owes a password change.
 * The redirect in LoginComponent only covers the sign-in path; direct URL entry needs this.
 */
export const mustChangePasswordGuard: CanActivateFn = () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    return auth.mustChangePassword() ? router.createUrlTree([AppRoute.ChangePassword]) : true;
}
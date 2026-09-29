import { HttpErrorResponse, HttpStatusCode } from "@angular/common/http";
import { Router } from "@angular/router";
import { AppRoute } from "../enums/app-route.enum";

// The resource is missing or outside the user's scope
const ACCESS_DENIED_STATUSES: readonly HttpStatusCode[] = [
    HttpStatusCode.Forbidden,
    HttpStatusCode.NotFound,
];

export function isAccessDenied(error: unknown): boolean {
    return error instanceof HttpErrorResponse && ACCESS_DENIED_STATUSES.includes(error.status);
}

/*
 * For the primary load of a page opened from a shared link (id in the URL).
 * The error interceptor already showed the toast; this only leaves the dead page.
 * replaceUrl: Back must not return to the dead link and bounce again.
 * Returns true when it navigated away.
 */
export function leaveIfAccessDenied(error: unknown, router: Router): boolean {
    if (!isAccessDenied(error)) {
        return false;
    }
    void router.navigate(['/' + AppRoute.Home], { replaceUrl: true });
    return true;
}
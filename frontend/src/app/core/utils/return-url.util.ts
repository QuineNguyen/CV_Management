import { AppRoute } from "../enums/app-route.enum";

const PROTOCOL_RELATIVE_PREFIX = '//';

/**
 * Normalises a returnUrl read from the query string.
 *
 * - Only root-relative paths are accepted. An absolute or protocol-relative value would let a
 * crafted link bounce the user to an external site right after sign-in, and a value pointing back
 * at the login page would loop. Both cases return null so the caller falls back to Home.
 */
export function sanitizeReturnUrl(raw: string | null | undefined): string | null {
    const value = raw?.trim();
    if (!value || !value.startsWith('/') || value.startsWith(PROTOCOL_RELATIVE_PREFIX)) {
        return null;
    }
    const path = value.split('?')[0].split('#')[0];
    return path === withLeadingSlash(AppRoute.Login) ? null : value;
}

function withLeadingSlash(route: string): string {
    return route.startsWith('/') ? route : `/${route}`;
}
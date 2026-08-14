package com.training.cvmanagementbe.config.auth;

/*
 * Route constants for authentication endpoints.
 * They must be compile-time constants: annotations cannot reference enum values,
 * so PublicEndpoint and AuthController both read them from here
 */
public final class AuthRoutes {

    public static final String LOGIN = "/auth/login";
    public static final String GOOGLE_LOGIN = "/auth/google";
    public static final String LOGOUT = "/auth/logout";
    public static final String CHANGE_PASSWORD = "/auth/change-password";

    // Admin-only; deliberately not listed in PublicEndpoint.
    public static final String ADMIN_RESET_PASSWORD = "/admin/users/{userId}/reset-password";

    private AuthRoutes() {}
}

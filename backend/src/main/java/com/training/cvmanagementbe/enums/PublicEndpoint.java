package com.training.cvmanagementbe.enums;

import com.training.cvmanagementbe.constant.AuthPath;

import java.util.Arrays;

/** Endpoints reachable without a JWT. Single source of truth for SecurityConfig. */
public enum PublicEndpoint {

    HEALTH("/health"),
    LOGIN(AuthPath.LOGIN),
    GOOGLE_LOGIN(AuthPath.GOOGLE_LOGIN),
    SMOKE("/smoke/**"),
    DOCS("/docs"),
    SWAGGER_UI("/swagger-ui/**"),
    SWAGGER_UI_HTML("/swagger-ui.html"),
    API_DOCS("/v3/api-docs/**");

    private final String pattern;

    PublicEndpoint(String pattern) {
        this.pattern = pattern;
    }

    public String pattern() {
        return pattern;
    }

    /**
     * Returns an array of URL patterns for all public endpoints defined in this enum.
     *
     * @return an array of URL pattern strings
     */
    public static String[] patterns() {
        return Arrays.stream(values()).map(PublicEndpoint::pattern).toArray(String[]::new);
    }
}

package com.training.cvmanagementbe.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
 * Turns a notification link into an absolute URL for links inside an email body.
 * Reads the same FRONTEND_BASE_URL as email-service, which builds the button's actionUrl.
 */
@Component
public class FrontendUrlResolver {

    private final String baseUrl;

    public FrontendUrlResolver(@Value("${app.frontend-base-url:http://localhost:4200}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String absolute(String link) {
        return baseUrl + "/" + link;
    }
}

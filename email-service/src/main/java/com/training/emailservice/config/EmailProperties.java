package com.training.emailservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/*
 * app.email.* settings
 * @param retryDelayMs  delay before each retry; 5 minutes in production
 * @param maxRetries    retries after the first attempt
 */
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        String from,
        String fromName,
        String appName,
        String frontendBaseUrl,
        long retryDelayMs,
        int maxRetries
) {
}

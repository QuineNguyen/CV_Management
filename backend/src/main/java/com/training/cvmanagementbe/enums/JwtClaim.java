package com.training.cvmanagementbe.enums;

/**
 * Enum representing custom claim keys used within JWT tokens.
 * Serves as a single source of truth to prevent hardcoded claim string literals.
 */
public enum JwtClaim {

    ROLE("role"),      // Key representing user role in JWT payload
    USER_ID("userId"); // Key representing user ID in JWT payload

    private final String key; // Attribute storing the string key corresponding to the claim

    // Constructor initializing the claim key string for each enum constant
    JwtClaim(String key) {
        this.key = key;
    }

    // Method returning the claim key string used when setting or extracting claims from JWT
    public String getKey() {
        return key;
    }
}

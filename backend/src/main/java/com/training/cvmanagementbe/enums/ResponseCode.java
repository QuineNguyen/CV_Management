package com.training.cvmanagementbe.enums;

/*
 * Codes for successful responses, the counterpart of ErrorCode
 *
 * - Kept as an enum rather than a literal so the envelope never carries a hardcoded string
 * and so additional success codes (partial results, accepted-for-processing) have a home.
 */
public enum ResponseCode {

    SUCCESS("Request completed successfully");

    private final String message;

    ResponseCode(String message) {
        this.message = message;
    }

    public String code() {
        return name();
    }

    public String message() {
        return message;
    }
}

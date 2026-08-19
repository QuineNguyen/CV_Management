package com.training.cvmanagementbe.enums;

import java.util.Arrays;

public enum StorageContentType {

    JPEG("image/jpeg"),
    PNG("image/png"),
    OCTET_STREAM("application/octet-stream");

    private final String value;

    StorageContentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    // Returns the declared type when recognised, otherwise the neutral fallback.
    public static String resolveOrFallback(String declared) {
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(declared))
                .findFirst()
                .orElse(OCTET_STREAM)
                .getValue();
    }
}

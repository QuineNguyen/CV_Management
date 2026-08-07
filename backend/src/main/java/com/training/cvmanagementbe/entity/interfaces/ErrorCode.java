package com.training.cvmanagementbe.entity.interfaces;

public interface ErrorCode {
    /** Stable identifier sent to clients. Equals the enum constant name by design. */
    String code();

    /** Fallback message for logs and direct API consumers. */
    String message();
}

package com.training.cvmanagementbe.enums.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Whitelist for the sortBy query parameter
@Getter
@RequiredArgsConstructor
public enum AuditLogSortField {
    CREATED_AT(AuditLogField.CREATED_AT),
    ACTION(AuditLogField.ACTION),
    TARGET_TYPE(AuditLogField.TARGET_TYPE);

    private final AuditLogField field;

    public String getProperty() {
        return field.getProperty();
    }
}

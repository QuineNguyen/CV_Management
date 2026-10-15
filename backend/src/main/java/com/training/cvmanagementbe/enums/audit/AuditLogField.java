package com.training.cvmanagementbe.enums.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// AuditLog entity properties used in criteria queries and sorting
@Getter
@RequiredArgsConstructor
public enum AuditLogField {
    ID("id"),
    ACTOR_ID("actorId"),
    ACTION("action"),
    TARGET_TYPE("targetType"),
    CREATED_AT("createdAt");

    private final String property;
}

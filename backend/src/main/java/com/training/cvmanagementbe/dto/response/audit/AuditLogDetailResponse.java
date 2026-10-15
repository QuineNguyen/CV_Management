package com.training.cvmanagementbe.dto.response.audit;

import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.users.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

// Detail panel: The list row plus the raw values, usually JSON written by AuditLogger
@Schema(name = "AuditLogDetailResponse", description = "Response body for detailed audit log entry")
public record AuditLogDetailResponse(
        UUID id,
        String actorName,
        String actorEmail,
        Role actorRole,
        Action action,
        TargetType targetType,
        UUID targetId,
        String oldValue,
        String newValue,
        LocalDateTime createdAt
) {
}

package com.training.cvmanagementbe.dto.response.audit;

import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.users.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One row of the audit list. Old and new values are left out: They can hold whole CV snapshots,
 * so they come from the detail endpoint only.
 */
@Schema(name = "AuditLogResponse", description = "Response body for an audit log entry")
public record AuditLogResponse(
        UUID id,
        String actorName,
        String actorEmail,
        Role actorRole,
        Action action,
        TargetType targetType,
        UUID targetId,
        LocalDateTime createdAt
) {
}

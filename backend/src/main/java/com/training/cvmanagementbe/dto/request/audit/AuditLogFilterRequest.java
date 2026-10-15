package com.training.cvmanagementbe.dto.request.audit;

import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

// Every criterion is optional; dates are inclusive whole days
@Schema(name = "AuditLogFilterRequest", description = "Request body for filtering audit log entries")
public record AuditLogFilterRequest(
        UUID actorId,
        Action action,
        TargetType targetType,
        LocalDate fromDate,
        LocalDate toDate
) {
}

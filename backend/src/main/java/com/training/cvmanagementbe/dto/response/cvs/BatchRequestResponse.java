package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.BatchRequestStatus;
import com.training.cvmanagementbe.enums.cvs.BatchTargetType;
import com.training.cvmanagementbe.enums.cvs.Language;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(name = "BatchRequestResponse", description = "Response for batch request")
public record BatchRequestResponse(
        UUID id,
        BatchTargetType targetType,
        // Department / team name or "N selected employee(s)"
        String targetLabel,
        Language language,
        String reason,
        LocalDateTime deadline,
        int totalCount,
        int processedCount,
        int errorCount,
        BatchRequestStatus status,
        LocalDateTime createdAt,
        String createdByName
) {
}

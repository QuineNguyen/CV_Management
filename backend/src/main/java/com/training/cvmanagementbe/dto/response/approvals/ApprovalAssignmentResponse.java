package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * An assignment as the API exposes it. The entity is not returned directly: it would leak audit
 * columns and, more importantly, would tie the wire format to the table so a column rename becomes
 * a breaking API change.
 */
@Schema(name = "ApprovalAssignmentResponse", description = "An assignment as the API exposes it")
public record ApprovalAssignmentResponse(
        UUID id,
        ApprovalLevel level,
        int reviewRound,
        String reason,
        LocalDateTime assignedAt,
        LocalDateTime dueAt,
        long slaRemainingMinutes
) {
}

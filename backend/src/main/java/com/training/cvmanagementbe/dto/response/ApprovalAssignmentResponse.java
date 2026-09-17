package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.ApprovalLevel;
import com.training.cvmanagementbe.enums.AssignmentStatus;
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
        UUID draftId,
        ApprovalLevel level,
        UUID assigneeId,
        String assigneeName,
        int reviewRound,
        AssignmentStatus status,
        String reason,
        LocalDateTime assignedAt,
        LocalDateTime dueAt,
        LocalDateTime closedAt,
        long slaRemainingMinutes
) {
}

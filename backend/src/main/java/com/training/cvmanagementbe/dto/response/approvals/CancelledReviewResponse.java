package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;
import com.training.cvmanagementbe.enums.cvs.Language;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * A review taken off the signed-in reviewer's queue because an administrator cancelled the draft.
 * The queue drops such an item silently; this row is where the reviewer reads why.
 * - assignmentId: the list's track key, nothing else.
 */
@Schema(name = "CancelledReviewResponse", description = "A review cancelled while assigned to the caller")
public record CancelledReviewResponse(
        UUID assignmentId,
        Language cvLanguage,
        String profileName,
        String employeeName,
        ApprovalLevel level,
        int reviewRound,
        LocalDateTime assignedAt,
        LocalDateTime cancelledAt,
        String cancelledByName,
        String reason
) {
}

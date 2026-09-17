package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.ApprovalLevel;
import com.training.cvmanagementbe.enums.Language;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One row of the approval queue.
 * - slaRemainingMinutes is computed server-side on purpose: the client clock is not
 * trustworthy for a deadline and two reviewers in different time zones must see the same urgency.
 * Negative means overdue.
 * @param slaRemainingMinutes left until dueAt; negative once past it
 */
@Schema(name = "ApprovalQueueItem", description = "One row of the approval queue")
public record ApprovalQueueItem(
        UUID assignmentId,
        UUID draftId,
        UUID cvId,
        Language cvLanguage,
        String profileName,
        String employeeName,
        ApprovalLevel level,
        int reviewRound,
        LocalDateTime assignedAt,
        LocalDateTime dueAt,
        long slaRemainingMinutes
) {
}

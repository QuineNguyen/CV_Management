package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.ApprovalLevel;
import com.training.cvmanagementbe.enums.DraftStatus;
import com.training.cvmanagementbe.enums.Language;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One row of the admin oversight list: every draft currently under review, whoever holds it.
 * This is the counterpart of ApprovalQueueItem - the queue is scoped to one assignee, this list
 * deliberately is not, because supervising means seeing other people's work.
 */
@Schema(name = "PendingDraftResponse", description = "A draft waiting for approval, seen by an administrator")
public record PendingDraftResponse(
        UUID draftId,
        UUID cvId,
        Language language,
        String profileName,
        String employeeName,
        DraftStatus status,
        ApprovalLevel level,
        int reviewRound,
        String assigneeName,
        LocalDateTime submittedAt,
        LocalDateTime dueAt,
        long slaRemainingMinutes
) {
}

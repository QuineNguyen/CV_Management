package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.ApprovalLevel;

import java.util.UUID;

/*
 * Raised when a reviewer rejects a draft.
 * - Same empty seam as DraftSubmittedEvent
 * @param level         the level that rejected (the notification names tech lead or HR)
 * @param reviewRound   the round that just ended
 */
public record DraftRejectedEvent(
        UUID draftId,
        UUID ownerId,
        ApprovalLevel level,
        int reviewRound
) {
}

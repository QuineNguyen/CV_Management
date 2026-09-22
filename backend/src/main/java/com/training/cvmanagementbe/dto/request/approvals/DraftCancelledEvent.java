package com.training.cvmanagementbe.dto.request.approvals;

import com.training.cvmanagementbe.enums.DraftStatus;

import java.util.UUID;

/*
 * Published after a cancel commits
 */
public record DraftCancelledEvent(
        UUID draftId,
        UUID ownerId,
        UUID assigneeId,
        DraftStatus previousStatus,
        String reason
) {
}

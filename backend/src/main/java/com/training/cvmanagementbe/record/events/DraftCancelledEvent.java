package com.training.cvmanagementbe.record.events;

import com.training.cvmanagementbe.enums.cvs.DraftStatus;

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

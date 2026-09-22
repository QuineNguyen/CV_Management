package com.training.cvmanagementbe.record;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;

import java.util.UUID;

/*
 * Raised after an approval commits its writes.
 * - LEVEL_1: nextAssigneeId is the HR who now owns the draft.
 * - LEVEL_2: versionNumber is the version just published, for the owner's notification.
 */
public record DraftApprovedEvent(
        UUID draftId,
        ApprovalLevel level,
        UUID ownerId,
        UUID nextAssigneeId,
        Integer versionNumber
) {

    public static DraftApprovedEvent forwarded(UUID draftId, UUID ownerId, UUID hrAssigneeId) {
        return new DraftApprovedEvent(draftId, ApprovalLevel.LEVEL_1, ownerId, hrAssigneeId, null);
    }

    public static DraftApprovedEvent published(UUID draftId, UUID ownerId, int versionNumber) {
        return new DraftApprovedEvent(draftId, ApprovalLevel.LEVEL_2, ownerId, null, versionNumber);
    }
}

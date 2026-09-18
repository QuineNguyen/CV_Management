package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.DraftStatus;

import java.util.UUID;

/*
 * Result of one approve action.
 * versionId is null at level 1 and set at level 2, where the approval publishes.
 */
public record DraftApproveResponse(
        UUID draftId,
        DraftStatus newStatus,
        UUID versionId
) {
}

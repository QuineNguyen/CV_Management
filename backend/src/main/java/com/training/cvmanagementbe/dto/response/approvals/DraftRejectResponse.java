package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.DraftStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/*
 * Result of rejecting a draft.
 * - newStatus: always REJECTED; returned so the client never has to assume it.
 * - commentCount: how many anchored comments were stored, for the confirmation toast.
 */
@Schema(name = "DraftRejectResponse", description = "Result of rejecting a draft")
public record DraftRejectResponse(
        DraftStatus newStatus,
        int commentCount
) {
}

package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/*
 * Result of one cancel action.
 * The two counts are reported rather than left implicit: the UI says what was stopped
 * ("1 assignment closed, 3 comments resolved") instead of a bare success message.
 */
@Schema(name = "DraftCancelResponse", description = "Result of cancelling a draft")
public record DraftCancelResponse(
        UUID draftId,
        DraftStatus newStatus,
        int assignmentsCancelled,
        int commentsResolved
) {
}

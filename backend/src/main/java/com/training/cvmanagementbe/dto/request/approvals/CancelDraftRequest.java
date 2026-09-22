package com.training.cvmanagementbe.dto.request.approvals;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/*
 * Body of a cancel action.
 * The reason is optional here on purpose: the owner dropping their own draft is not asked for one,
 * while an admin stopping a running review must give one. Only the service knows which case it is,
 * so the rule lives there rather than in an annotation.
 */
@Schema(name = "CancelDraftRequest", description = "Optional reason for cancelling a draft")
public record CancelDraftRequest(

        @Size(max = 2000)
        String reason
) {
}

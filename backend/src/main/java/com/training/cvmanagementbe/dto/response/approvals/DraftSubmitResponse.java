package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.DraftStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/*
 * Result of submitting a draft for approval.
 * - level1Skipped is reported rather than left to be inferred from newStatus:
 * landing one PENDING_HR straight away is surprising enough that the UI has to say why
 * and the reason ("you are the tech lead who would have reviewed it") is not derivable client-side.
 * @param newStatus     PENDING_TECH_LEAD or PENDING_HR when level 1 was skipped
 * @param reviewRound   the round this submission opened
 */
@Schema(name = "DraftSubmitResponse", description = "Result of submitting a draft for approval")
public record DraftSubmitResponse(
        UUID draftId,
        DraftStatus newStatus,
        int reviewRound,
        boolean level1Skipped
) {
}

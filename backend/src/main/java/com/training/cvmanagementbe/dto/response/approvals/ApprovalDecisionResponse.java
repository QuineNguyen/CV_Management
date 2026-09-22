package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;
import com.training.cvmanagementbe.enums.approvals.DecisionResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

// One past verdict, shown to a reviewer opening a draft that has already been through a round.
@Schema(name = "ApprovalDecisionResponse", description = "One past verdict, shown to a reviewer opening a draft that has already been through a round")
public record ApprovalDecisionResponse(
        UUID id,
        ApprovalLevel level,
        int reviewRound,
        String approverName,
        DecisionResult result,
        String reason,
        LocalDateTime decidedAt
) {
}

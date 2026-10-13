package com.training.cvmanagementbe.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// Dashboard of a tech lead, scoped to the teams they lead.
@Schema(name = "TechLeadDashboardResponse", description = "Dashboard of a tech lead, scoped to the teams they lead")
public record TechLeadDashboardResponse(
        // My teams
        long teamMemberCount,
        long teamCvCount,
        long teamPendingRequestCount,

        // My approval queue
        long myPendingApprovalCount,
        long myOverdueApprovalCount,

        RequestDeadlineWidget requestDeadlines,

        LocalDateTime generatedAt
) {
}

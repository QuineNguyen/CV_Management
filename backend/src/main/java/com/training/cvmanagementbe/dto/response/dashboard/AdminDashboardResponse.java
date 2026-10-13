package com.training.cvmanagementbe.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// Company-wide dashboard, shared by Admin and HR
@Schema(name = "AdminDashboardResponse", description = "Company-wide dashboard, shared by Admin and HR")
public record AdminDashboardResponse(
        // Company overview
        long totalEmployees,
        long totalActiveCvs,
        long cvsWithVersion,
        long cvsWithoutVersion,

        // Waiting for action
        long pendingUpdateRequestCount,
        long pendingProfileUpdateCount,
        long draftsUnderReviewCount,
        long myPendingApprovalCount,

        // Widgets
        RequestDeadlineWidget requestDeadlines,
        ApprovalSlaWidget approvalSla,

        LocalDateTime generatedAt
) {
}

package com.training.cvmanagementbe.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// Personal dashboard. No approval widget: an employee approves nothing.
@Schema(name = "EmployeeDashboardResponse", description = "Personal dashboard. No approval widget: an employee approves nothing")
public record EmployeeDashboardResponse(
        // My CVs
        long myCvCount,
        long openDraftCount,
        long draftsInReviewCount,

        // Requests sent to me
        long pendingRequestCount,
        RequestDeadlineWidget requestDeadlines,

        LocalDateTime generatedAt
) {
}

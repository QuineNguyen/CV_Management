package com.training.cvmanagementbe.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// Assignments past their SLA: Total count plus the longest-waiting rows.
@Schema(name = "ApprovalSlaWidget", description = "Assignments past their SLA: Total count plus the longest-waiting rows")
public record ApprovalSlaWidget(
        long overdueCount,
        List<ApprovalOverdueSummary> items
) {
}

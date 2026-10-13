package com.training.cvmanagementbe.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/*
 * "Due soon / Overdue" widget, shared by all three dashboards.
 * Counts cover the whole scope; items holds only the most urgent rows.
 */
@Schema(name = "RequestDeadlineWidget", description = "Due soon / Overdue widget, shared by all three dashboards")
public record RequestDeadlineWidget(
        int approachingDays,
        long approachingCount,
        long lastDayCount,
        long overdueCount,
        List<OverdueRequestSummary> items
) {

    public static RequestDeadlineWidget empty(int approachingDays) {
        return new RequestDeadlineWidget(approachingDays, 0, 0, 0, List.of());
    }
}

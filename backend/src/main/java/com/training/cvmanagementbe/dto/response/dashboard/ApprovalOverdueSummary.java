package com.training.cvmanagementbe.dto.response.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One open assignment past its review SLA (Admin/HR only).
 * - assignmentId: Row key on the client
 * - daysOverdue: Whole days past dueAt; 0 means less than a day
 */
@Schema(name = "ApprovalOverdueSummary", description = "One open assignment past its review SLA (Admin/HR only)")
public record ApprovalOverdueSummary(
        UUID assignmentId,
        String cvOwnerName,
        String assigneeName,
        int level,
        LocalDateTime dueAt,
        long daysOverdue
) {
}

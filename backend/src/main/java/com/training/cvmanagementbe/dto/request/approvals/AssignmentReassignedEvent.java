package com.training.cvmanagementbe.dto.request.approvals;

import com.training.cvmanagementbe.enums.ApprovalLevel;

import java.util.UUID;

/*
 * Published after a handover commits
 */
public record AssignmentReassignedEvent(
        UUID draftId,
        UUID previousAssigneeId,
        UUID newAssigneeId,
        ApprovalLevel level,
        String reason
) {
}

package com.training.cvmanagementbe.record.events;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;

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

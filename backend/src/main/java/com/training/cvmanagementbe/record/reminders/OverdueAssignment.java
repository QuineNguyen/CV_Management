package com.training.cvmanagementbe.record.reminders;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;

import java.time.LocalDateTime;
import java.util.UUID;

// Open assignment past due_at, with every name the reminders need already resolved
public record OverdueAssignment(
        UUID assignmentId,
        UUID draftId,
        UUID cvId,
        UUID assigneeId,
        String assigneeName,
        String ownerName,
        String profileName,
        String language,
        ApprovalLevel level,
        LocalDateTime assignedAt,
        LocalDateTime dueAt,
        long overdueDays
) {
    // Same "Profile (EN)" label the approval notifications use
    public String cvLabel() {
        return "%s (%s)".formatted(profileName, language);
    }
}

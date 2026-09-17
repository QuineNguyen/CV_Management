package com.training.cvmanagementbe.enums;

// Lifecycle of an approval assignment. Used as string parameters in native queries.
public enum AssignmentStatus {

    // Open work. At most one per draft at any time.
    ASSIGNED,

    // The assignee decided - approved or rejected.
    COMPLETED,

    // Admin handed the work to someone else; the replacement row is ASSIGNED.
    REASSIGNED,

    // Level 1 only: the sole eligible tech lead was the submitter. No assignee.
    SKIPPED,

    // The draft was cancelled while this row was still open. Distinct from COMPLETED
    // precisely because nobody decided anything.
    CANCELLED
}

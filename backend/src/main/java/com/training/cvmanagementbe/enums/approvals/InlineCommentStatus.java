package com.training.cvmanagementbe.enums.approvals;

/*
 * Lifecycle of one inline comment.
 * - OPEN: written in the round that is still being worked on.
 * - RESOLVED: its round ended by a resubmit. Kept as history, never deleted.
 * Constant names math the CHECK constraint on inline_comments.status.
 */
public enum InlineCommentStatus {
    OPEN,
    RESOLVED
}

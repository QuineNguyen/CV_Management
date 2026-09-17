package com.training.cvmanagementbe.enums;

/*
 * Outcome of one approval decision. Each decision is an immutable row, so a draft rejected
 * three rounds in a row still answers "who decided what and when" in full.
 */
public enum DecisionResult {

    APPROVED,

    // Requires an overall reason.
    REJECTED,

    // Level 1 bypassed because the submitter was the only eligible reviewer. Requires a reason and carries no approver.
    SKIPPED;

    // The two results that cannot be recorded without a reason.
    public boolean requiresReason() {
        return this != APPROVED;
    }
}

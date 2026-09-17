package com.training.cvmanagementbe.record;

import java.util.UUID;

/*
 * What the approver resolver decided. Internal value type - never serialised to the API.
 *
 * - Two outcomes only. "Nobody could be found" is not one of them: an empty candidate list that is
 * not explained by "the submitter is the only one" is a broken invariant and the resolver throws rather
 * than returning a third state the caller could forget to handle.
 * @param skipped       true only at level 1, when the sole eligible tech lead is the submitter
 * @param assigneeId    the chosen approver; null exactly when skipped
 * @param reason        always present - the audit trail for why this person or why nobody
 */
public record ResolverResult(boolean skipped, UUID assigneeId, String reason) {

    public static ResolverResult assignedTo(UUID assigneeId, String reason) {
        return new ResolverResult(false, assigneeId, reason);
    }

    public static ResolverResult skipped(String reason) {
        return new ResolverResult(true, null, reason);
    }
}

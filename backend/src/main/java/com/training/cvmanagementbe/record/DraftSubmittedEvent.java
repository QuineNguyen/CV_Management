package com.training.cvmanagementbe.record;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;

import java.util.UUID;

/*
 * Published when a draft enters the approval flow.
 * - Deliberately an empty seam: nothing listens to it yet. Phase 4 adds the notification listener
 * here instead of reopening ApprovalServiceImpl, which is the point - the workflow should not grow
 * an email dependency and a listener cannot accidentally change the transaction outcome
 * the way an inline call would.
 * - Published inside the transaction, so a listener registered with
 * @TransactionalEventListener(phase = AFTER_COMMIT) will only fire once to submit has
 * actually committed - no notification for a rolled-back submit.
 * @param draftId       the draft that was submitted
 * @param assigneeId    who now owns it; null when level 1 was skipped and level 2 is being resolved
 * @param level         the draft now waits at
 * @param skipped       whether level 1 was bypassed
 */
public record DraftSubmittedEvent(
        UUID draftId,
        UUID assigneeId,
        ApprovalLevel level,
        boolean skipped
) {
}

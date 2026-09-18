package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.response.*;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/*
 * The two-level approval flow.
 * - This phase covers entering the flow - submitting, assigning and opening a draft to read it.
 * Approving, rejecting, inline comments, cancelling and reassigning are added by the following
 * parts of this stage and all of them keep the same contract: a draft transition is a
 * compare-and-set and only the assignee may act on an open assignment.
 */
public interface ApprovalService {

    /*
     * Submits the caller's own draft for approval.
     * - Resolves the level-1 reviewer and moves the draft to PENDING_TECH_LEAD, or
     * straight to PENDING_HR when the only eligible tech lead is the submitter.
     * From that moment the content is read-only for everyone including the owner
     */
    DraftSubmitResponse submit(UUID draftId);

    // The caller's own open assignments, most urgent first. Never anyone else's.
    PagedResponse<ApprovalQueueItem> getQueue(Pageable pageable);

    // Opens a draft for review. Answers 403 to anyone but the current assignee.
    DraftReviewResponse openForReview(UUID draftId);

    // Approves at the current level; level 2 also publishes. CAS on (status, assignee)
    DraftApproveResponse approve(UUID draftId);
}

package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.approvals.CancelDraftRequest;
import com.training.cvmanagementbe.dto.request.approvals.ReassignRequest;
import com.training.cvmanagementbe.dto.request.approvals.RejectDraftRequest;
import com.training.cvmanagementbe.dto.request.approvals.ReplyCommentRequest;
import com.training.cvmanagementbe.dto.response.approvals.*;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    // Reviews cancelled while assigned to the caller, with the reason. Same scope as the queue.
    PagedResponse<CancelledReviewResponse> getCancelledReviews(Pageable pageable);

    // Opens a draft for review. Answers 403 to anyone but the current assignee.
    DraftReviewResponse openForReview(UUID draftId);

    // Approves at the current level; level 2 also publishes. CAS on (status, assignee)
    DraftApproveResponse approve(UUID draftId);

    // Rejects at the current level.
    DraftRejectResponse reject(UUID draftId, RejectDraftRequest request);

    // Re-submits a rejected draft for approval.
    DraftSubmitResponse resubmit(UUID draftId);

    // Replies to an inline comment.
    InlineCommentResponse replyToComment(UUID commentId, ReplyCommentRequest request);

    // ---------- Admin oversight ----------

    // Every draft under review, whoever holds it. Admin only; enforced at the controller.
    PagedResponse<PendingDraftResponse> getPendingDrafts(Pageable pageable);

    // Owner cancels DRAFT/REJECTED; Admin cancels any open draft, with a reason when it is pending.
    DraftCancelResponse cancel(UUID draftId, CancelDraftRequest request);

    // People who may take over the open assignment of this draft, the least loaded first.
    List<ReassignCandidateResponse> getReassignCandidates(UUID draftId);

    // Closes the open assignment as REASSIGNED and opens a new one with a fresh SLA.
    ReassignResponse reassign(UUID draftId, ReassignRequest request);
}

package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.approvals.CancelDraftRequest;
import com.training.cvmanagementbe.dto.request.approvals.ReassignRequest;
import com.training.cvmanagementbe.dto.request.approvals.RejectDraftRequest;
import com.training.cvmanagementbe.dto.request.approvals.ReplyCommentRequest;
import com.training.cvmanagementbe.dto.response.approvals.*;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.enums.approvals.ApprovalSortField;
import com.training.cvmanagementbe.enums.approvals.PendingDraftSortField;
import com.training.cvmanagementbe.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPath.APPROVALS)
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Two-level CV approval: submit, queue and review")
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping(ApiPath.APPROVAL_QUEUE)
    @Operation(summary = "List the caller's open approval assignments, most urgent first")
    public ResponseEntity<PagedResponse<ApprovalQueueItem>> getQueue(
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "DUE_AT") ApprovalSortField sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(
                PageDefaults.clampPage(page),
                PageDefaults.clampSize(size),
                Sort.by(direction, sortBy.getProperty())
        );

        return ResponseEntity.ok(approvalService.getQueue(pageable));
    }

    @GetMapping(ApiPath.CANCELLED_QUEUE)
    @Operation(summary = "List reviews cancelled while assigned to you")
    public ResponseEntity<PagedResponse<CancelledReviewResponse>> getCancelledReviews(
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size),
                Sort.by(Sort.Direction.DESC, ApprovalSortField.CLOSED_AT.getProperty()));

        return ResponseEntity.ok(approvalService.getCancelledReviews(pageable));
    }

    @PostMapping(ApiPath.DRAFT_SUBMIT)
    @Operation(summary = "Submit an own draft for approval; locks its content until a decision")
    public ResponseEntity<DraftSubmitResponse> submit(@PathVariable UUID draftId) {
        return ResponseEntity.ok(approvalService.submit(draftId));
    }

    @GetMapping(ApiPath.DRAFT_REVIEW)
    @Operation(summary = "Open a draft for review; 403 unless the caller is its current assignee")
    public ResponseEntity<DraftReviewResponse> openForReview(@PathVariable UUID draftId) {
        return ResponseEntity.ok(approvalService.openForReview(draftId));
    }

    @PostMapping(ApiPath.DRAFT_APPROVE)
    @Operation(summary = "Approve the draft at its current level; level 2 also published a new version")
    public ResponseEntity<DraftApproveResponse> approve(@PathVariable UUID draftId) {
        return ResponseEntity.ok(approvalService.approve(draftId));
    }

    @PostMapping(ApiPath.DRAFT_REJECT)
    @Operation(summary = "Reject the draft at its current level; locks its content until resubmission")
    public ResponseEntity<DraftRejectResponse> reject(@PathVariable UUID draftId, @Valid @RequestBody RejectDraftRequest request) {
        return ResponseEntity.ok(approvalService.reject(draftId, request));
    }

    @PostMapping(ApiPath.DRAFT_RESUBMIT)
    public ResponseEntity<DraftSubmitResponse> resubmit(@PathVariable UUID draftId) {
        return ResponseEntity.ok(approvalService.resubmit(draftId));
    }

    @PostMapping(ApiPath.COMMENT_REPLY)
    public ResponseEntity<InlineCommentResponse> replyToComment(@PathVariable UUID commentId, @Valid @RequestBody ReplyCommentRequest request) {
        return ResponseEntity.ok(approvalService.replyToComment(commentId, request));
    }

    @GetMapping(ApiPath.PENDING_DRAFTS)
    @PreAuthorize(AuthorityExpression.ADMIN)
    @Operation(summary = "List every draft under review, for supervision")
    public ResponseEntity<PagedResponse<PendingDraftResponse>> getPendingDrafts(
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "SUBMITTED_AT") PendingDraftSortField sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        // Oldest submission first: supervision starts with whoever has waited long waited longest.
        Sort sort = PageDefaults.sortBy(direction, sortBy.getProperty(),
                PendingDraftSortField.SUBMITTED_AT.getProperty());
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);

        return ResponseEntity.ok(approvalService.getPendingDrafts(pageable));
    }

    // No @PreAuthorize: owner and admin both reach this and only the service can tell them apart.
    @PostMapping(ApiPath.DRAFT_CANCEL)
    @Operation(summary = "Cancel a draft (owner before review, admin at any point)")
    public ResponseEntity<DraftCancelResponse> cancel(
            @PathVariable UUID draftId,
            @Valid @RequestBody(required = false)CancelDraftRequest request
    ) {
        return ResponseEntity.ok(approvalService.cancel(draftId, request));
    }

    @GetMapping(ApiPath.DRAFT_REASSIGN_CANDIDATES)
    @PreAuthorize(AuthorityExpression.ADMIN)
    @Operation(summary = "List the people who may take over this draft")
    public ResponseEntity<List<ReassignCandidateResponse>> getReassignCandidates(@PathVariable UUID draftId) {
        return ResponseEntity.ok(approvalService.getReassignCandidates(draftId));
    }

    @PostMapping(ApiPath.DRAFT_REASSIGN)
    @PreAuthorize(AuthorityExpression.ADMIN)
    @Operation(summary = "Transfer the open assignment to another reviewer")
    public ResponseEntity<ReassignResponse> reassign(@PathVariable UUID draftId,
                                                     @Valid @RequestBody ReassignRequest request) {
        return ResponseEntity.ok(approvalService.reassign(draftId, request));
    }
}

package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;
import com.training.cvmanagementbe.enums.approvals.AssignmentStatus;
import com.training.cvmanagementbe.enums.approvals.DecisionResult;
import com.training.cvmanagementbe.enums.approvals.InlineCommentStatus;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;
import com.training.cvmanagementbe.record.DraftRejectedEvent;
import com.training.cvmanagementbe.dto.request.approvals.InlineCommentRequest;
import com.training.cvmanagementbe.dto.request.approvals.RejectDraftRequest;
import com.training.cvmanagementbe.dto.request.approvals.ReplyCommentRequest;
import com.training.cvmanagementbe.dto.response.approvals.*;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.CvDraftResponse;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.*;
import com.training.cvmanagementbe.repository.*;
import com.training.cvmanagementbe.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Entry point of the two-level approval flow.
 * Three rules shape everything here:
 * - Every transition is a compare-and-set. The draft moves with
 * UPDATE ... WHERE id = ? AND status IN (...); zero rows affected is a 409, never
 * silent overwrite. Two tabs, a double click and a mid-flight admin action all land here.
 * - Approvers are resolved before anything is written. The resolver only reads, so a
 * submit that cannot find a reviewer fails without having touched the draft - and the CAS
 * becomes the single gate in front of every insert.
 * - The reason is written, always. Team membership keeps no history, so the
 * sentence stored on the assignment is the only thing that can still explain a past round.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalServiceImpl implements ApprovalService {

    // A draft may enter the flow from a fresh draft or from a rejected one.
    private static final Set<DraftStatus> SUBMITTABLE_STATUSES =
            EnumSet.of(DraftStatus.DRAFT, DraftStatus.REJECTED);

    // The dedicated resubmit endpoint accepts a rejected draft only.
    private static final Set<DraftStatus> RESUBMITTABLE_STATUSES =
            EnumSet.of(DraftStatus.REJECTED);

    // Threads stay open for replies while a round is running and while the owner revises.
    private static final Set<DraftStatus> REPLYABLE_STATUSES =
            EnumSet.of(DraftStatus.PENDING_TECH_LEAD, DraftStatus.PENDING_HR, DraftStatus.REJECTED);

    /*
     * Sections that must carry content before a draft can be submitted.
     * Listed as enum constants rather than strings so a renamed section key breaks the build
     */
    private static final List<CvSectionKey> REQUIRED_SECTIONS = List.of(
            CvSectionKey.PERSONAL_INFO,
            CvSectionKey.SKILLS,
            CvSectionKey.EXPERIENCE
    );

    // TODO [Phase-5]: read from system_configs once the configuration table exists.
    private static final Duration DEFAULT_SLA = Duration.ofDays(3);

    private final CvDraftRepository cvDraftRepository;
    private final CvRepository cvRepository;
    private final CvProfileRepository cvProfileRepository;
    private final UserRepository userRepository;
    private final ApprovalAssignmentRepository assignmentRepository;
    private final ApprovalDecisionRepository decisionRepository;
    private final InlineCommentRepository inlineCommentRepository;
    private final ApproverResolver approverResolver;
    private final VersionPublisher versionPublisher;
    private final CvContentCodec codec;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- Submit ----------

    /*
     * Kept accepting REJECTED for API compatibility. Both entry points run the same pipeline, so
     * a rejected draft gets the sticky reviewer whichever endpoint the client calls.
     */
    @Override
    @Transactional
    public DraftSubmitResponse submit(UUID draftId) {
        return enterApproval(draftId, SUBMITTABLE_STATUSES);
    }

    // ---------- Resubmit ----------
    @Override
    @Transactional
    public DraftSubmitResponse resubmit(UUID draftId) {
        return enterApproval(draftId, RESUBMITTABLE_STATUSES);
    }

    /*
     * Shared by submit and resubmit.
     * - A resubmit always restarts at level 1 - even after an HR rejection the tech lead reviews
     * again - unless the level-1 skip rule applies.
     * - Each level prefers the reviewer of the previous round.
     */
    private DraftSubmitResponse enterApproval(UUID draftId, Set<DraftStatus> acceptedStatuses) {
        UUID submitterId = CurrentActor.requireUserId();

        CvDraft draft = cvDraftRepository.findById(draftId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv draft", draftId));

        // Only the owner writes CV content, so only the owner submits it.
        if (!submitterId.equals(draft.getOwnerId())) {
            throw new ApiException.ForbiddenException(ErrorCode.NOT_CV_OWNER);
        }

        /*
         * Checked Here for a clear message and again in the CAS below for correctness. This read
         * cannot be the guard on its own: between it and to write, an Admin could cancel the draft.
         */
        if (!acceptedStatuses.contains(draft.getStatus())) {
            throw new ApiException.ConflictException(acceptedStatuses == RESUBMITTABLE_STATUSES
                    ? ErrorCode.DRAFT_NOT_REJECTED
                    : ErrorCode.STALE_STATE);
        }

        boolean resubmission = draft.getStatus() == DraftStatus.REJECTED;
        int previousRound = draft.getReviewRound();

        CvContent content = codec.read(draft.getContentJson());
        requireRequiredSections(content);

        Cv cv = cvRepository.findByIdAndLifecycleStatus(draft.getCvId(), LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv", draft.getCvId()));
        CvProfile profile = cvProfileRepository.findByIdAndLifecycleStatus(cv.getProfileId(), LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", cv.getProfileId()));

        /*
         * Resolved before any write. Both calls can throw 422 and a submit rejected for having no
         * reviewer must leave the draft exactly as it was rather than half-transitioned.
         * The submitter is passed explicitly rather than read from CurrentActor inside the resolver
         */
        // A first submit has no previous round, so both lookups stay null and nothing sticks.
        UUID previousLead = resubmission
                ? previousAssigneeOf(draftId, ApprovalLevel.LEVEL_1, previousRound)
                : null;
        ResolverResult level1 = approverResolver.resolveLevel1(profile, submitterId, previousLead);

        ResolverResult level2 = null;
        if (level1.skipped()) {
            UUID previousHr = resubmission
                    ? previousAssigneeOf(draftId, ApprovalLevel.LEVEL_2, previousRound)
                    : null;
            level2 = approverResolver.resolveLevel2(submitterId, previousHr);
        }

        DraftStatus nextStatus = level1.skipped() ? DraftStatus.PENDING_HR : DraftStatus.PENDING_TECH_LEAD;
        int nextRound = previousRound + 1;
        LocalDateTime now = LocalDateTime.now();

        // The gate. Everything below only runs because this matched exactly one row.
        int updated = cvDraftRepository.markSubmitted(draftId, nextStatus, nextRound, now, acceptedStatuses);
        if (updated == 0) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        // Earlier rounds are closed, not deleted. A no-op on a first submit.
        inlineCommentRepository.resolveEarlierRounds(
                draftId, nextRound, InlineCommentStatus.OPEN, InlineCommentStatus.RESOLVED
        );

        if (level1.skipped()) {
            recordSkippedLevel1(draftId, nextRound, level1, now);
            createAssignment(draftId, ApprovalLevel.LEVEL_2, level2.assigneeId(), nextRound,
                    level2.reason(), now);
            publish(draftId, ApprovalLevel.LEVEL_2, level2.assigneeId(), true);
        } else {
            createAssignment(draftId, ApprovalLevel.LEVEL_1, level1.assigneeId(), nextRound,
                    level1.reason(), now);
            publish(draftId, ApprovalLevel.LEVEL_1, level1.assigneeId(), false);
        }

        auditLogger.record(Action.SUBMIT_CV_DRAFT, TargetType.CV_DRAFT, draftId,
                draft.getStatus(), nextStatus);

        return new DraftSubmitResponse(draftId, nextStatus, nextRound, level1.skipped());
    }

    // ---------- Queue ----------

    @Override
    public PagedResponse<ApprovalQueueItem> getQueue(Pageable pageable) {
        UUID assigneeId = CurrentActor.requireUserId();

        Page<ApprovalAssignment> page = assignmentRepository.findByAssigneeIdAndStatus(
                assigneeId, AssignmentStatus.ASSIGNED, pageable
        );

        /*
         * Scoped by assignee in the query, not filtered afterward: the queue is an access
         * boundary, not a convenience view. Only the assignee sees the item and only the assignee
         * can open it.
         */
        return PagedResponse.of(page, toQueueItems(page.getContent()));
    }

    // ---------- Open for review ----------

    @Override
    public DraftReviewResponse openForReview(UUID draftId) {
        UUID viewerId = CurrentActor.requireUserId();

        ApprovalAssignment assignment = requireOwnAssignment(draftId, viewerId);

        CvDraft draft = cvDraftRepository.findById(draftId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv draft", draftId));
        Cv cv = cvRepository.findById(draft.getCvId())
                .orElseThrow(() -> new ApiException.NotFoundException("cv", draft.getCvId()));
        CvProfile profile = cvProfileRepository.findById(cv.getProfileId())
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", cv.getProfileId()));

        List<ApprovalDecision> decisions = decisionRepository.findByDraftIdOrderByDecidedAtAsc(draftId);

        Map<UUID, String> names = loadUserNames(collectUserIds(assignment, decisions, profile));
        LocalDateTime now = LocalDateTime.now();

        return new DraftReviewResponse(
                toResponse(draft, codec.read(draft.getContentJson())),
                cv.getLanguage(),
                profile.getName(),
                names.get(profile.getEmployeeId()),
                avatarUrlResolver.resolve(draft.getAvatarImageId()),
                toAssignmentResponse(assignment, now),
                decisions.stream().map(decision -> toDecisionResponse(decision, names)).toList()
        );
    }

    // ---------- Approve ----------

    /*
     * The transition is a CAS on (expected status, current assignee), split across two
     * guarded updates in one transaction - the draft status and the open assignment. Either
     * matching zero rows rolls everything back.
     */
    @Override
    @Transactional
    public DraftApproveResponse approve(UUID draftId) {
        UUID approverId = CurrentActor.requireUserId();

        ApprovalAssignment assignment = requireOwnAssignment(draftId, approverId);

        CvDraft draft = cvDraftRepository.findById(draftId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv draft", draftId));

        ApprovalLevel level = levelOf(draft.getStatus());

        // The open assignment and the draft status must describe the same step.
        if (level != assignment.approvalLevel()) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        return level == ApprovalLevel.LEVEL_1
                ? approveTechnicalReview(draft, assignment, approverId)
                : approveFormatReviewAndPublish(draft, assignment, approverId);
    }

    // Level 1: PENDING_TECH_LEAD -> PENDING_HR, then hand over to exactly one HR.
    private DraftApproveResponse approveTechnicalReview(CvDraft draft, ApprovalAssignment assignment,
                                                        UUID approverId) {
        // Resolved before any write, like submit: no reviewer found leaves the draft untouched.
        // Sticky looks at round N-1 only; when that round ended at level 1,
        // there is no HR to keep and the normal pipeline runs.
        UUID previousHr = previousAssigneeOf(draft.getId(), ApprovalLevel.LEVEL_2, assignment.getReviewRound() - 1);
        ResolverResult level2 = approverResolver.resolveLevel2(draft.getOwnerId(), previousHr);
        LocalDateTime now = LocalDateTime.now();

        transitionDraft(draft.getId(), DraftStatus.PENDING_TECH_LEAD, DraftStatus.PENDING_HR, approverId, now);
        recordDecision(assignment, approverId, DecisionResult.APPROVED, null, now);

        createAssignment(draft.getId(), ApprovalLevel.LEVEL_2, level2.assigneeId(),
                assignment.getReviewRound(), level2.reason(), now);

        auditLogger.record(Action.APPROVE_CV_DRAFT, TargetType.CV_DRAFT, draft.getId(),
                DraftStatus.PENDING_TECH_LEAD, DraftStatus.PENDING_HR);

        eventPublisher.publishEvent(
                DraftApprovedEvent.forwarded(draft.getId(), draft.getOwnerId(), level2.assigneeId())
        );

        return new DraftApproveResponse(draft.getId(), DraftStatus.PENDING_HR, null);
    }

    /*
     * Level 2: PENDING_HR -> PUBLISHED and publish in the same transaction.
     * The CAS row lock serializes two HR clicks, so at most one version is created.
     */
    private DraftApproveResponse approveFormatReviewAndPublish(CvDraft draft, ApprovalAssignment assignment,
                                                               UUID approverId) {
        LocalDateTime now = LocalDateTime.now();
        int reviewRound = assignment.getReviewRound();

        // Read everything from the draft first: the CAS clears the persistence context.
        CvContent content = codec.read(draft.getContentJson());
        UUID level1ApproverId = level1ApproverOf(draft.getId(), reviewRound);

        transitionDraft(draft.getId(), DraftStatus.PENDING_HR, DraftStatus.PUBLISHED, approverId, now);
        recordDecision(assignment, approverId, DecisionResult.APPROVED, null, now);

        // Creates the version and change log, completes pending update requests, links the draft.
        CvVersion version = versionPublisher.publish(PublishCommand.approval(
                draft.getCvId(),
                content,
                draft.getAvatarImageId(),
                draft.getOwnerId(),
                level1ApproverId,
                approverId,
                draft.getId()
        ));

        // TODO [Phase-7]: trigger multilingual structure sync when the published CV is not the
        // only language of its profile.

        auditLogger.record(Action.APPROVE_CV_DRAFT, TargetType.CV_DRAFT, draft.getId(),
                DraftStatus.PENDING_HR, DraftStatus.PUBLISHED);

        eventPublisher.publishEvent(
                DraftApprovedEvent.published(draft.getId(), draft.getOwnerId(), version.getVersionNumber())
        );

        return new DraftApproveResponse(draft.getId(), DraftStatus.PUBLISHED, version.getId());
    }

    // ---------- Reject ----------

    /*
     * PENDING_* -> REJECTED at either level.
     * - Same CAS pair as approve: the draft status and the open assignment. The assignment closes
     * as COMPLETED - somebody did decide - and the decision row carries the overall reason.
     * - Every anchor is validated before the first write, so one bad anchor leaves the draft
     * exactly as it was instead of rejected with half its comments.
     */
    @Override
    @Transactional
    public DraftRejectResponse reject(UUID draftId, RejectDraftRequest request) {
        UUID rejecterId = CurrentActor.requireUserId();

        ApprovalAssignment assignment = requireOwnAssignment(draftId, rejecterId);

        CvDraft draft = cvDraftRepository.findById(draftId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv draft", draftId));

        DraftStatus currentStatus = draft.getStatus();
        ApprovalLevel level = levelOf(currentStatus);

        if (level != assignment.approvalLevel()) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        // Read every thing from the draft first: the CAS clears the persistence context.
        CvContent content = codec.read(draft.getContentJson());
        UUID ownerId = draft.getOwnerId();
        int reviewRound = assignment.getReviewRound();
        String reason = request.reason().trim();
        LocalDateTime now = LocalDateTime.now();

        List<InlineComment> comments = request.commentsOrEmpty().stream()
                .map(comment -> toRootComment(draftId, reviewRound, rejecterId, comment, content, now))
                .toList();

        transitionDraft(draftId, currentStatus, DraftStatus.REJECTED, rejecterId, now);
        recordDecision(assignment, rejecterId, DecisionResult.REJECTED, reason, now);

        // Guarded on REJECTED so the reason cannot land on a draft that has already moved on.
        int updated = cvDraftRepository.updateLastRejectionReason(draftId, reason, DraftStatus.REJECTED);
        if (updated == 0) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        inlineCommentRepository.saveAll(comments);

        auditLogger.record(Action.REJECT_CV_DRAFT, TargetType.CV_DRAFT, draftId,
                currentStatus, DraftStatus.REJECTED);

        eventPublisher.publishEvent(new DraftRejectedEvent(draftId, ownerId, level, reviewRound));

        return new DraftRejectResponse(DraftStatus.REJECTED, comments.size());
    }

    // ---------- Reply ----------

    /*
     * Adds a reply to a thread.
     * - Who: the CV owner or the reviewer currently assigned. A reviewer whose round already ended
     * has no open assignment and therefore no access to the draft at all.
     * - Replies attach to the thread root, so a thread stays one level deep however it is used.
     * - A resubmit racing this call may resolve the root first; the reply then stays in that round
     * and is resolved by the next resubmit. Acceptable: it is still readable as history.
     */
    @Override
    @Transactional
    public InlineCommentResponse replyToComment(UUID commentId, ReplyCommentRequest request) {
        UUID actorId = CurrentActor.requireUserId();

        InlineComment target = inlineCommentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException.NotFoundException("inline comment", commentId));

        InlineComment root = target.isRoot()
                ? target
                : inlineCommentRepository.findById(target.getParentCommentId())
                .orElseThrow(() -> new ApiException.NotFoundException("inline comment", target.getParentCommentId()));

        CvDraft draft = cvDraftRepository.findById(root.getDraftId())
                .orElseThrow(() -> new ApiException.NotFoundException("cv draft", root.getDraftId()));

        requireCanReply(draft, actorId);

        if (!REPLYABLE_STATUSES.contains(draft.getStatus())) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        if (root.getStatus() == InlineCommentStatus.RESOLVED) {
            throw new ApiException.BusinessRuleException(ErrorCode.CANNOT_REPLY_RESOLVED);
        }

        InlineComment reply = inlineCommentRepository.save(InlineComment.builder()
                .draftId(root.getDraftId())
                .reviewRound(root.getReviewRound())
                .sectionKey(root.getSectionKey())
                .itemId(root.getItemId())
                .fieldKey(root.getFieldKey())
                .authorId(actorId)
                .content(request.content().trim())
                .status(InlineCommentStatus.OPEN)
                .parentCommentId(root.getId())
                .createdAt(LocalDateTime.now())
                .build());

        String authorName = userRepository.findById(actorId).map(User::getFullName).orElse(null);
        return toResponse(reply, authorName);
    }

    // ---------- Validation ----------

    // 404 when nothing is open, 403 when the open item belongs to someone else.
    private ApprovalAssignment requireOwnAssignment(UUID draftId, UUID actorId) {
        ApprovalAssignment assignment = assignmentRepository
                .findByDraftIdAndStatus(draftId, AssignmentStatus.ASSIGNED)
                .orElseThrow(() -> new ApiException.NotFoundException("approval assignment", draftId));

        if (!actorId.equals(assignment.getAssigneeId())) {
            throw new ApiException.ForbiddenException(ErrorCode.NOT_CURRENT_ASSIGNEE);
        }
        return assignment;
    }

    // Owner or the reviewer holding the open assignment.
    private void requireCanReply(CvDraft draft, UUID actorId) {
        if (actorId.equals(draft.getOwnerId())) {
            return;
        }

        boolean isCurrentAssignee = assignmentRepository
                .findByDraftIdAndStatus(draft.getId(), AssignmentStatus.ASSIGNED)
                .map(assignment -> actorId.equals(assignment.getAssigneeId()))
                .orElse(false);

        if (!isCurrentAssignee) {
            throw new ApiException.ForbiddenException(ErrorCode.CANNOT_REPLY_COMMENT);
        }
    }

    /*
     * Item required for REPEATED sections, and it must exist in this draft; always empty
     * for SINGLE ones. The field stays free-form - an empty field is still worth commenting on
     * and the frontend only offers the section's own fields.
     */
    private void requireValidAnchor(CvSectionKey section, String itemId, CvContent content) {
        boolean valid = section.repeated()
                ? itemId != null && codec.itemsById(content, section).containsKey(itemId)
                : itemId == null;

        if (!valid) {
            throw new ApiException.BusinessRuleException(ErrorCode.INVALID_COMMENT_ANCHOR);
        }
    }

    // Only the two pending statuses can be approved; anything else was decided or cancelled.
    private ApprovalLevel levelOf(DraftStatus status) {
        return switch (status) {
            case PENDING_TECH_LEAD -> ApprovalLevel.LEVEL_1;
            case PENDING_HR -> ApprovalLevel.LEVEL_2;
            default -> throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        };
    }

    /*
     * Personal info, skills and experience must carry content.
     * - Named sections come back in the message so an API consumer sees what to fix; the UI does
     * not depend on parsing it, because it holds the same draft content and lists the gaps itself.
     */
    private void requireRequiredSections(CvContent content) {
        List<CvSectionKey> missing = REQUIRED_SECTIONS.stream()
                .filter(section -> isEmptySection(content, section))
                .toList();

        if (missing.isEmpty()) {
            return;
        }

        String names = missing.stream().map(CvSectionKey::key).collect(Collectors.joining(", "));
        throw new ApiException.BusinessRuleException(
                ErrorCode.DRAFT_MISSING_REQUIRED_SECTIONS,
                "Fill in these sections before submitting: " + names
        );
    }

    private boolean isEmptySection(CvContent content, CvSectionKey section) {
        return switch (section) {
            // A personal info block exists on every draft, so "empty" means the one field that
            // identifies the person is blank.
            case PERSONAL_INFO -> content.personalInfo() == null
                    || content.personalInfo().fullName() == null
                    || content.personalInfo().fullName().isBlank();
            default -> content.entriesOf(section).isEmpty();
        };
    }

    // ---------- Writes ----------

    /*
     * Records that level 1 was bypassed. Two rows, not one: the assignment says the level was
     * handled and carries no assignee, the decision says what the outcome was. Reporting reads the
     * decisions table for "how many CVs skipped technical review" and it must find an entry there.
     */
    private void recordSkippedLevel1(UUID draftId, int reviewRound, ResolverResult level1, LocalDateTime now) {
        ApprovalAssignment skipped = ApprovalAssignment.builder()
                .draftId(draftId)
                .level(ApprovalLevel.LEVEL_1.value())
                .assigneeId(null)
                .assignedBy(null)
                .reviewRound(reviewRound)
                .status(AssignmentStatus.SKIPPED)
                .reason(level1.reason())
                .assignedAt(now)
                // Never acted on, so the due date is formal only; it closes in the same instant.
                .dueAt(now)
                .closedAt(now)
                .build();
        assignmentRepository.save(skipped);

        decisionRepository.save(ApprovalDecision.builder()
                .draftId(draftId)
                .level(ApprovalLevel.LEVEL_1.value())
                .reviewRound(reviewRound)
                .approverId(null)
                .result(DecisionResult.SKIPPED)
                .reason(level1.reason())
                .decidedAt(now)
                .build());

        auditLogger.record(Action.SKIP_APPROVAL_LEVEL, TargetType.CV_DRAFT, draftId,
                null, level1.reason());
    }

    /*
     * Opens one assignment. assignedBy stays null: the system chose and a value there is
     * reserved for the Admin reassign path, so filling it in here would make a manual
     * handover indistinguishable from an automatic one.
     * - The insert is also guarded by uk_approval_assignments_assigned, which is what
     * actually enforces "at most one open assignment per draft" - a race that slips past
     * the CAS still hits the index and surfaces as APPROVAL_ALREADY_ASSIGNED.
     */
    private void createAssignment(UUID draftId, ApprovalLevel level, UUID assigneeId,
                                  int reviewRound, String reason, LocalDateTime now) {
        ApprovalAssignment assignment = ApprovalAssignment.builder()
                .draftId(draftId)
                .level(level.value())
                .assigneeId(assigneeId)
                .assignedBy(null)
                .reviewRound(reviewRound)
                .status(AssignmentStatus.ASSIGNED)
                .reason(reason)
                .assignedAt(now)
                .dueAt(now.plus(DEFAULT_SLA))
                .build();

        ApprovalAssignment saved = assignmentRepository.save(assignment);

        auditLogger.record(Action.ASSIGN_APPROVER, TargetType.APPROVAL_ASSIGNMENT, saved.getId(),
                null, assigneeId);
    }

    // Draft half of the CAS. Zero rows = someone moved the draft first.
    private void transitionDraft(UUID draftId, DraftStatus expected, DraftStatus next,
                                 UUID actorId, LocalDateTime now) {
        int updated = cvDraftRepository.transitionStatus(draftId, expected, next, actorId, now);
        if (updated == 0) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }
    }

    /*
     * Assignment half of the CAS, then the decision row.
     * The close only matches while the assignment is still ASSIGNED to this approver,
     * so an Admin reassign landing mid-click turns into a 409 here.
     * @param reason    null when approving; the overall reason when rejecting
     */
    private void recordDecision(ApprovalAssignment assignment, UUID reviewerId,
                                DecisionResult result, String reason, LocalDateTime now) {
        int closed = assignmentRepository.closeForAssignee(
                assignment.getId(), reviewerId,
                AssignmentStatus.ASSIGNED, AssignmentStatus.COMPLETED, now
        );
        if (closed == 0) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        // Never overwritten: every decision is a new row.
        decisionRepository.save(ApprovalDecision.builder()
                .draftId(assignment.getDraftId())
                .level(assignment.approvalLevel().value())
                .reviewRound(assignment.getReviewRound())
                .approverId(reviewerId)
                .result(result)
                .reason(reason)
                .decidedAt(now)
                .build());
    }

    // Validates the anchor and builds an unsaved root comment of this round.
    private InlineComment toRootComment(UUID draftId, int reviewRound, UUID authorId,
                                        InlineCommentRequest request, CvContent content, LocalDateTime now) {
        String itemId = blankToNull(request.itemId());
        requireValidAnchor(request.sectionKey(), itemId, content);

        return InlineComment.builder()
                .draftId(draftId)
                .reviewRound(reviewRound)
                .sectionKey(request.sectionKey())
                .itemId(itemId)
                .fieldKey(blankToNull(request.fieldKey()))
                .authorId(authorId)
                .content(request.content().trim())
                .status(InlineCommentStatus.OPEN)
                .parentCommentId(null)
                .createdAt(now)
                .build();
    }

    /*
     * Reviewer who decided this level in the given round, for sticky assignment.
     * COMPLETED covers both outcomes; a SKIPPED level or a round that never reached this level
     * has no such row, which is exactly the "nobody to keep" case.
     */
    private UUID previousAssigneeOf(UUID draftId, ApprovalLevel level, int reviewRound) {
        if (reviewRound < 1) {
            return null;
        }
        return assignmentRepository
                .findFirstByDraftIdAndLevelAndReviewRoundAndStatusOrderByClosedAtDesc(
                        draftId, level.value(), reviewRound, AssignmentStatus.COMPLETED
                )
                .map(ApprovalAssignment::getAssigneeId)
                .orElse(null);
    }

    // Null when level 1 was skipped in this round: nobody vouched technically at that level.
    private UUID level1ApproverOf(UUID draftId, int reviewRound) {
        return decisionRepository
                .findByDraftIdAndLevelAndReviewRoundAndResult(
                        draftId, ApprovalLevel.LEVEL_1.value(), reviewRound, DecisionResult.APPROVED
                )
                .map(ApprovalDecision::getApproverId)
                .orElse(null);
    }

    private void publish(UUID draftId, ApprovalLevel level, UUID assigneeId, boolean skipped) {
        eventPublisher.publishEvent(new DraftSubmittedEvent(draftId, assigneeId, level, skipped));
    }

    // ---------- Mapping ----------

    // Resolves every name in one pass per table, so a page of 50 items is four queries, not 200.
    private List<ApprovalQueueItem> toQueueItems(List<ApprovalAssignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }

        Map<UUID, CvDraft> draftsById = indexById(
                cvDraftRepository.findAllById(idsOf(assignments, ApprovalAssignment::getDraftId)),
                CvDraft::getId
        );

        Map<UUID, Cv> cvsById = indexById(
                cvRepository.findAllById(idsOf(draftsById.values(), CvDraft::getCvId)),
                Cv::getId
        );

        Map<UUID, CvProfile> profilesById = indexById(
                cvProfileRepository.findAllById(idsOf(cvsById.values(), Cv::getProfileId)),
                CvProfile::getId
        );

        Map<UUID, String> names = loadUserNames(
                idsOf(profilesById.values(), CvProfile::getEmployeeId)
        );

        LocalDateTime now = LocalDateTime.now();

        return assignments.stream().map(assignment -> {
            CvDraft draft = draftsById.get(assignment.getDraftId());
            Cv cv = draft == null ? null : cvsById.get(draft.getCvId());
            CvProfile profile = cv == null ? null : profilesById.get(cv.getProfileId());

            return new ApprovalQueueItem(
                    assignment.getId(),
                    assignment.getDraftId(),
                    cv == null ? null : cv.getLanguage(),
                    profile == null ? null : profile.getName(),
                    profile == null ? null : names.get(profile.getEmployeeId()),
                    assignment.approvalLevel(),
                    assignment.getReviewRound(),
                    assignment.getAssignedAt(),
                    assignment.getDueAt(),
                    minutesUntil(assignment.getDueAt(), now)
            );
        }).toList();
    }

    private CvDraftResponse toResponse(CvDraft draft, CvContent content) {
        return new CvDraftResponse(
                draft.getId(),
                draft.getCvId(),
                draft.getStatus(),
                draft.getReviewRound(),
                content,
                draft.getAvatarImageId(),
                avatarUrlResolver.resolve(draft.getAvatarImageId()),
                draft.getLastRejectionReason(),
                content.submittable(),
                content.untranslatedItemCount(),
                draft.getSubmittedAt(),
                draft.getUpdatedAt(),
                forDraft(draft.getId())
        );
    }

    private ApprovalAssignmentResponse toAssignmentResponse(ApprovalAssignment assignment,
                                                            LocalDateTime now) {
        return new ApprovalAssignmentResponse(
                assignment.getId(),
                assignment.approvalLevel(),
                assignment.getReviewRound(),
                assignment.getReason(),
                assignment.getAssignedAt(),
                assignment.getDueAt(),
                minutesUntil(assignment.getDueAt(), now)
        );
    }

    private ApprovalDecisionResponse toDecisionResponse(ApprovalDecision decision, Map<UUID, String> names) {
        return new ApprovalDecisionResponse(
                decision.getId(),
                decision.approvalLevel(),
                decision.getReviewRound(),
                decision.getApproverId() == null ? null : names.get(decision.getApproverId()),
                decision.getResult(),
                decision.getReason(),
                decision.getDecidedAt()
        );
    }

    // ---------- Inline comment assembler ----------

    public List<InlineCommentResponse> forDraft(UUID draftId) {
        List<InlineComment> comments = inlineCommentRepository.findByDraftIdOrderByCreatedAtAsc(draftId);
        if (comments.isEmpty()) {
            return List.of();
        }

        Set<UUID> authorIds = comments.stream()
                .map(InlineComment::getAuthorId)
                .collect(Collectors.toSet());

        Map<UUID, String> names = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return comments.stream()
                .map(comment -> toResponse(comment, names.get(comment.getAuthorId())))
                .toList();
    }

    public InlineCommentResponse toResponse(InlineComment comment, String authorName) {
        return new InlineCommentResponse(
                comment.getId(),
                comment.getReviewRound(),
                comment.getSectionKey(),
                comment.getItemId(),
                comment.getFieldKey(),
                authorName,
                comment.getContent(),
                comment.getStatus(),
                comment.getParentCommentId(),
                comment.getCreatedAt()
        );
    }

    // ---------- Small helpers ----------

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Negative once the deadline has passed, which is exactly how the UI decides its badge tone.
    private long minutesUntil(LocalDateTime dueAt, LocalDateTime now) {
        return dueAt == null ? 0L : Duration.between(now, dueAt).toMinutes();
    }

    private Set<UUID> collectUserIds(ApprovalAssignment assignment,
                                     List<ApprovalDecision> decisions,
                                     CvProfile profile) {
        Set<UUID> ids = new HashSet<>();
        ids.add(profile.getEmployeeId());
        ids.add(assignment.getAssigneeId());
        decisions.forEach(decision -> ids.add(decision.getApproverId()));
        ids.remove(null);
        return ids;
    }

    private Map<UUID, String> loadUserNames(Collection<UUID> userIds) {
        return userIds.isEmpty() ? Map.of() : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private <T> Set<UUID> idsOf(Collection<T> source, Function<T, UUID> extractor) {
        return source.stream().map(extractor).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private <T> Map<UUID, T> indexById(Collection<T> rows, Function<T, UUID> idExtractor) {
        return rows.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
    }
}

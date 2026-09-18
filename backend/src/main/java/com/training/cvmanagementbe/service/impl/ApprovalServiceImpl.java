package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.response.*;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.*;
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
    private final ApproverResolver approverResolver;
    private final VersionPublisher versionPublisher;
    private final CvContentCodec codec;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- Submit ----------

    @Override
    @Transactional
    public DraftSubmitResponse submit(UUID draftId) {
        UUID submitterId = CurrentActor.requireUserId();

        CvDraft draft = cvDraftRepository.findById(draftId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv draft", draftId));

        // Only the owner writes CV content, so only the owner submits it.
        if (!submitterId.equals(draft.getOwnerId())) {
            throw new ApiException.ForbiddenException(ErrorCode.NOT_CV_OWNER);
        }

        /*
         * Checked here for a clear message and again in the CAS below for correctness. This read
         * cannot be the guard on its own: between it and to write, an Admin could cancel the draft.
         */
        if (!SUBMITTABLE_STATUSES.contains(draft.getStatus())) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        CvContent content = codec.read(draft.getContentJson());
        requireRequiredSections(content);

        Cv cv = cvRepository.findByIdAndLifecycleStatus(draft.getCvId(), LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv", draft.getCvId()));
        CvProfile profile = cvProfileRepository.findByIdAndLifecycleStatus(cv.getProfileId(), LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", cv.getProfileId()));

        /*
         * Resolved before any write. Both calls can throw 422 and a submit rejected for having no
         * reviewer must leave the draft exactly as it was rather than half-transitioned.
         * The submitter is passed explicitly rather than read from CurrentActor inside the
         * resolver
         */
        ResolverResult level1 = approverResolver.resolveLevel1(profile, submitterId);
        ResolverResult level2 = level1.skipped() ? approverResolver.resolveLevel2(submitterId) : null;

        DraftStatus nextStatus = level1.skipped() ? DraftStatus.PENDING_HR : DraftStatus.PENDING_TECH_LEAD;
        int nextRound = draft.getReviewRound() + 1;
        LocalDateTime now = LocalDateTime.now();

        // The gate. Everything below only runs because this matched exactly one row.
        int updated = cvDraftRepository.markSubmitted(draftId, nextStatus, nextRound, now, SUBMITTABLE_STATUSES);
        if (updated == 0) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        // TODO [Phase-3 part 3]: flip OPEN inline comments of earlier rounds to RESOLVED.
        // The inline-comments entity arrives with the rejection loop; nothing writes OPEN
        // comments yet, so there is nothing to resolve at this point.

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
        ResolverResult level2 = approverResolver.resolveLevel2(draft.getOwnerId());
        LocalDateTime now = LocalDateTime.now();

        transitionDraft(draft.getId(), DraftStatus.PENDING_TECH_LEAD, DraftStatus.PENDING_HR, approverId, now);
        recordApproval(assignment, approverId, now);

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
        recordApproval(assignment, approverId, now);

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
     */
    private void recordApproval(ApprovalAssignment assignment, UUID approverId, LocalDateTime now) {
        int closed = assignmentRepository.closeForAssignee(
                assignment.getId(), approverId,
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
                .approverId(approverId)
                .result(DecisionResult.APPROVED)
                .reason(null)
                .decidedAt(now)
                .build());
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
                draft.getUpdatedAt()
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

    // ---------- Small helpers ----------

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

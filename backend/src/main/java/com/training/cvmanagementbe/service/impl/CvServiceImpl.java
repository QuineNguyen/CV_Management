package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.cvs.CvCreateRequest;
import com.training.cvmanagementbe.dto.request.cvs.CvDeleteRequest;
import com.training.cvmanagementbe.dto.request.cvs.CvEditRequest;
import com.training.cvmanagementbe.dto.request.cvs.CvRollbackRequest;
import com.training.cvmanagementbe.dto.response.approvals.InlineCommentResponse;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.*;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.ContentDiff;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.record.cvs.PublishCommand;
import com.training.cvmanagementbe.record.cvs.RollbackAuditDetail;
import com.training.cvmanagementbe.record.events.CvDeletedEvent;
import com.training.cvmanagementbe.record.events.CvRestoredEvent;
import com.training.cvmanagementbe.record.events.CvRolledBackEvent;
import com.training.cvmanagementbe.repository.*;
import com.training.cvmanagementbe.repository.projection.CvVersionRef;
import com.training.cvmanagementbe.service.CvService;
import com.training.cvmanagementbe.service.DiffService;
import com.training.cvmanagementbe.service.SkillService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CvServiceImpl implements CvService {

    private final CvRepository cvRepository;
    private final CvVersionRepository cvVersionRepository;
    private final CvDraftRepository cvDraftRepository;
    private final CvProfileRepository cvProfileRepository;
    private final UserRepository userRepository;
    private final InlineCommentRepository inlineCommentRepository;
    private final UpdateRequestRepository updateRequestRepository;
    private final CvSearchRepository cvSearchRepository;
    private final AnchoredNoteCodec anchoredNoteCodec;
    private final UpdateRequestCanceller updateRequestCanceller;
    private final VersionPublisher versionPublisher;
    private final CvContentCodec codec;
    private final CvItemIdGuard itemIdGuard;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;
    private final DiffService diffService;
    private final SkillService skillService;
    private final EntityManager entityManager;

    // ---------- Queries ----------

    @Override
    public CvDetailResponse getById(UUID cvId) {
        Cv cv = requireCv(cvId);
        CvProfile profile = requireProfileAnyStatus(cv.getProfileId());
        requireCanRead(profile.getEmployeeId());

        Optional<CvVersion> current = cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(cvId);

        // Draft content and review feedback belong to the owner only.
        // Reviewers/Admin read pending drafts via the approval screens, which check assignment.
        boolean owner = isOwner(profile.getEmployeeId());

        CvDraftResponse openDraft = owner
                ? cvDraftRepository.findByCvIdAndStatusIn(cvId, CvDraft.OPEN_STATUSES)
                        .map(this::toDraftResponse)
                        .orElse(null)
                : null;

        UUID avatarImageId = current.map(CvVersion::getAvatarImageId).orElse(null);

        // Cancellation reason is addressed to the owner, not to every reader.
        DraftCancellationResponse lastCancellation = owner && openDraft == null
                ? lastCancellationOf(cvId, profile.getEmployeeId())
                : null;

        return new CvDetailResponse(
                toResponse(cv, profile, resolveEmployeeName(profile.getEmployeeId()), current.orElse(null)),
                current.map(this::toSummary).orElse(null),
                current.map(version -> readForDisplay(version.getContentJson())).orElse(null),
                avatarImageId,
                avatarUrlResolver.resolve(avatarImageId),
                openDraft,
                lastCancellation
        );
    }

    @Override
    public List<CvResponse> listByProfile(UUID profileId, boolean includeDeleted) {
        CvProfile profile = requireProfileAnyStatus(profileId);
        requireCanRead(profile.getEmployeeId());

        List<Cv> cvs = includeDeleted
                ? cvRepository.findByProfileIdOrderByLanguageAsc(profileId)
                : cvRepository.findByProfileIdAndLifecycleStatusOrderByLanguageAsc(profileId, LifecycleStatus.ACTIVE);

        String employeeName = resolveEmployeeName(profile.getEmployeeId());
        return cvs.stream().map(cv -> toResponse(cv, profile, employeeName, currentVersionOf(cv))).toList();
    }

    @Override
    public CvContent getScaffold(UUID profileId) {
        CvProfile profile = requireActiveProfile(profileId);
        requireOwner(profile.getEmployeeId());

        return newCvContent(findActiveMaster(profileId).orElse(null), requireUser(profile.getEmployeeId()));
    }

    @Override
    public PagedResponse<CvResponse> listDeleted(Pageable pageable) {
        requireAdminOrHr();

        Page<Cv> page = cvRepository.findByLifecycleStatus(LifecycleStatus.DELETED, pageable);
        Map<UUID, CvProfile> profiles = loadProfiles(page.getContent());
        Map<UUID, String> names = loadEmployeeNames(profiles.values());

        List<CvResponse> content = page.getContent().stream()
                .map(cv -> {
                    CvProfile profile = profiles.get(cv.getProfileId());
                    String employeeName = profile == null ? null : names.get(profile.getEmployeeId());
                    return toResponse(cv, profile, employeeName, currentVersionOf(cv));
                })
                .toList();

        return PagedResponse.of(page, content);
    }

    /*
     * Timeline page. Names are resolved for this page only and each row carries its
     * predecessor id so "compare with previous" works across page boundaries.
     */
    @Override
    public PagedResponse<CvVersionHistoryItem> listVersions(UUID cvId, Pageable pageable) {
        Cv cv = requireCv(cvId);
        requireCanRead(requireProfileAnyStatus(cv.getProfileId()).getEmployeeId());

        Page<CvVersionRef> page = cvVersionRepository.findByCvId(cvId, pageable);
        List<CvVersionRef> rows = page.getContent();

        Map<UUID, String> names = loadUserNames(rows.stream()
                .flatMap(row -> Stream.of(
                        row.getAuthoredBy(), row.getLevel1ApproverId(), row.getLevel2ApproverId()))
                .toList());
        Map<UUID, Integer> rollbackSources = loadRollbackSourceNumbers(rows);
        Map<Integer, UUID> predecessors = loadPredecessorIds(cvId, rows);

        List<CvVersionHistoryItem> content = rows.stream()
                .map(row -> toHistoryItem(row, names, rollbackSources, predecessors))
                .toList();

        return PagedResponse.of(page, content);
    }

    /*
     * Compares two published versions. Without fromVersionId the "to" version is compared
     * with an empty CV - how v1 shows what it introduced. Same read scope as the CV; a version
     * of another CV reads as not found.
     */
    @Override
    public VersionDiffResponse diffVersions(UUID cvId, UUID fromVersionId, UUID toVersionId) {
        Cv cv = requireCv(cvId);
        requireCanRead(requireProfileAnyStatus(cv.getProfileId()).getEmployeeId());

        if (toVersionId.equals(fromVersionId)) {
            throw new ApiException.BusinessRuleException(ErrorCode.DIFF_SAME_VERSION);
        }

        CvVersion to = requireVersionOf(cvId, toVersionId);
        CvVersion from = fromVersionId == null ? null : requireVersionOf(cvId, fromVersionId);

        ContentDiff diff = diffService.diff(
                from == null ? null : readForDisplay(from.getContentJson()),
                from == null ? null : from.getAvatarImageId(),
                readForDisplay(to.getContentJson()),
                to.getAvatarImageId()
        );

        return new VersionDiffResponse(
                from == null ? null : toDiffSide(from),
                toDiffSide(to),
                diff.stats(),
                diff.sections()
        );
    }

    /*
     * Notes of the PENDING request(s) on this CV, flat per anchor so the editor pins each one.
     * Same scope as reading the CV. Bounded by the pending slot rule, so not paged.
     */
    @Override
    public List<PendingNoteResponse> getPendingNotes(UUID cvId) {
        Cv cv = requireCv(cvId);
        requireCanRead(requireProfileAnyStatus(cv.getProfileId()).getEmployeeId());

        List<UpdateRequest> pending = updateRequestRepository
                .findByCvIdAndStatusOrderByCreatedAtAsc(cvId, RequestStatus.PENDING);
        if (pending.isEmpty()) {
            return List.of();
        }

        Set<UUID> creatorIds = pending.stream()
                .map(UpdateRequest::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return pending.stream()
                .flatMap(request -> anchoredNoteCodec.read(request.getAnchoredNotes()).stream()
                        .map(note -> new PendingNoteResponse(
                                note.sectionKey(),
                                note.itemId(),
                                note.fieldKey(),
                                note.note(),
                                names.get(request.getCreatedBy()),
                                request.getCreatedAt()
                        )))
                .toList();
    }

    // ---------- Commands ----------

    @Override
    @Transactional
    public CvResponse create(UUID profileId, CvCreateRequest request) {
        CvProfile profile = requireActiveProfile(profileId);
        requireOwner(profile.getEmployeeId());
        validateLanguageAvailable(profileId, request.language());

        User owner = requireUser(profile.getEmployeeId());

        // Read before the new row exists: The first CV of a profile becomes its master.
        Cv master = findActiveMaster(profileId).orElse(null);

        Cv cv = new Cv();
        cv.setProfileId(profileId);
        cv.setLanguage(request.language());
        cv.setLifecycleStatus(LifecycleStatus.ACTIVE);
        // First CV of a profile is its master; every later one hangs off the current master.
        cv.setMasterCvId(master == null ? null : master.getId());

        Cv saved = cvRepository.saveAndFlush(cv);

        // Identity fields always come from the account, whatever the client sent.
        // Item ids are filled in before anything downstream tries to match on them.
        CvContent content = normalise(request.content() == null
                ? newCvContent(master, owner)
                : codec.normaliseItemIds(request.content()));

        // Checked after ids are filled in, so a generated id is validated like any other.
        itemIdGuard.requireOwnedOrNew(profileId, content);

        // Typed skill names join the shared catalogue here.
        content = skillService.linkCatalog(content);

        /*
         * Linked before publishing, not after. An Admin/HR owner publishes v1 inside
         * publishOrDraft and that is the moment a pending request becomes COMPLETED - a request
         * still unlinked at that point would stay PENDING forever even though the CV it asked for
         * now exists and is published.
         */
        cvRepository.linkOldestPendingRequest(
                saved.getId(),
                profileId,
                profile.getEmployeeId(),
                request.language().name(),
                CurrentActor.requireUserId(),
                LocalDateTime.now()
        );

        publishOrDraft(saved, content, request.avatarImageId(), profile.getEmployeeId());

        CvResponse response = toResponse(saved, profile, owner.getFullName(), currentVersionOf(saved));
        auditLogger.record(Action.CREATE_CV, TargetType.CV, saved.getId(), null, response);
        return response;
    }

    @Override
    @Transactional
    public CvEditResponse edit(UUID cvId, CvEditRequest request) {
        // Locked first: Structure sync takes this row before it touches this CV's draft
        Cv cv = requireActiveCvForUpdate(cvId);
        CvProfile profile = requireActiveProfile(cv.getProfileId());

        // No path exists for anyone but the owner to write CV content.
        if (!CurrentActor.requireUserId().equals(profile.getEmployeeId())) {
            throw new ApiException.ForbiddenException(ErrorCode.NOT_CV_OWNER);
        }

        // Locking read, so a draft that sync committed while this request waited is seen here
        Optional<CvDraft> open = cvDraftRepository.findByCvIdAndStatusInForUpdate(cv.getId(), CvDraft.OPEN_STATUSES);
        verifyDraftToken(open.orElse(null), request.expectedDraftUpdatedAt());

        // Frozen for review whichever branch the owner's role takes
        if (open.filter(CvDraft::isContentLocked).isPresent()) {
            throw new ApiException.BusinessRuleException(ErrorCode.DRAFT_CONTENT_LOCKED);
        }

        if (publishedDirectly()) {
            CvContent content = normalise(request.content());
            itemIdGuard.requireOwnedOrNew(profile.getId(), content);
            content = skillService.linkCatalog(content);

            // An open draft - for Admin/HR, one that structure sync created - closes with this version
            CvVersion version = versionPublisher.publish(PublishCommand.directEdit(
                    cv.getId(),
                    content,
                    request.avatarImageId(),
                    profile.getEmployeeId(),
                    open.map(CvDraft::getId).orElse(null)
            ));

            // Skipping the approval flow is exactly the case that must leave a trail.
            auditLogger.record(Action.DIRECT_EDIT_PUBLISH, TargetType.CV, cv.getId(),
                    null, version.getVersionNumber());

            return CvEditResponse.ofVersion(toSummary(version));
        }

        return CvEditResponse.ofDraft(writeDraft(cv, profile, open, request));
    }

    @Override
    @Transactional
    public void delete(UUID cvId, CvDeleteRequest request) {
        Cv cv = requireActiveCv(cvId);
        requireAdminOrHr();
        validateNoPendingDraft(cvId);

        CvProfile profile = requireProfileAnyStatus(cv.getProfileId());
        CvResponse before = toResponse(cv, profile, resolveEmployeeName(profile.getEmployeeId()), currentVersionOf(cv));

        // Resolved before anything is written: with no valid successor the whole delete is refused,
        // rather than half-applied.
        Cv successor = cv.isMaster()
                ? resolveSuccessorMaster(cv, request == null ? null : request.newMasterCvId())
                : null;

        UUID actorId = CurrentActor.requireUserId();
        LocalDateTime deletedAt = LocalDateTime.now();

        cv.setLifecycleStatus(LifecycleStatus.DELETED);
        cv.setDeletedBy(actorId);
        cv.setDeletedAt(deletedAt);

        /*
         * Flushed before the successor is promoted and that order is the whole point. While this
         * row is still ACTIVE with a null master_cv_id, it and the successor both satisfy "the
         * active master of this profile" - and uk_cvs_active_master rejects the second one.
         * Deleting first vacates the slot the successor is about to take.
         */

        cvRepository.saveAndFlush(cv);

        if (successor != null) {
            promoteToMaster(cv, successor);
        }

        // A request pointing at a deleted CV can never be answered, so it stops asking
        updateRequestCanceller.cancelPendingForCv(cvId, actorId, deletedAt);

        auditLogger.record(Action.DELETE_CV, TargetType.CV, cvId, before, null);
        eventPublisher.publishEvent(new CvDeletedEvent(cvId, profile.getEmployeeId(), actorId));
    }

    @Override
    @Transactional
    public CvResponse restore(UUID cvId) {
        Cv cv = cvRepository.findById(cvId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv", cvId));
        requireAdminOrHr();

        if (cv.getLifecycleStatus() != LifecycleStatus.DELETED) {
            throw new ApiException.BusinessRuleException(ErrorCode.CV_NOT_DELETED);
        }

        // 1. The parent profile must be back first - a CV cannot outlive its profile.
        CvProfile profile = requireProfileAnyStatus(cv.getProfileId());
        if (profile.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new ApiException.BusinessRuleException(ErrorCode.CV_PROFILE_DELETED);
        }

        // 2. The (profile, language) slot must still be free - DELETED rows never held it.
        if (cvRepository.existsByProfileIdAndLanguageAndLifecycleStatus(
                profile.getId(), cv.getLanguage(), LifecycleStatus.ACTIVE)) {
            throw new ApiException.BusinessRuleException(ErrorCode.CV_SLOT_OCCUPIED);
        }

        // 3. Master relationship must stay single-valued.
        realignMasterOnRestore(cv, profile.getId());

        cv.setLifecycleStatus(LifecycleStatus.ACTIVE);
        cv.setDeletedBy(null);
        cv.setDeletedAt(null);
        Cv saved = cvRepository.save(cv);

        CvResponse after = toResponse(saved, profile, resolveEmployeeName(profile.getEmployeeId()), currentVersionOf(saved));
        auditLogger.record(Action.RESTORE_CV, TargetType.CV, cvId, null, after);
        eventPublisher.publishEvent(new CvRestoredEvent(cvId, profile.getEmployeeId(), CurrentActor.requireUserId()));

        return after;
    }

    /*
     * Rollback publishes a new version copied from an older one: content, avatar and the
     * three identity columns come from the source. No version is edited or deleted and the
     * person who clicked is recorded only in the audit log - never on cv_versions.
     */
    @Override
    @Transactional
    public CvVersionHistoryItem rollback(UUID cvId, CvRollbackRequest request) {
        requireAdminOrHr();

        // Locked before validating, so two rollbacks of one CV check against the same current version.
        Cv cv = requireActiveCvForUpdate(cvId);

        CvVersion source = requireVersionOf(cvId, request.targetVersionId());
        CvVersion current = requireCurrentVersion(cvId);

        validateNotCurrent(source, current);
        // Only PENDING_* blocks; an owner's DRAFT or REJECTED draft stays open untouched.
        validateNoPendingDraft(cvId);

        // VersionPublisher numbers it MAX + 1, writes the change log and completes PENDING requests.
        CvVersion published = versionPublisher.publish(PublishCommand.rollback(
                cvId,
                codec.read(source.getContentJson()),
                source.getAvatarImageId(),
                source.getAuthoredBy(),
                source.getLevel1ApproverId(),
                source.getLevel2ApproverId(),
                source.getId()
        ));

        // Approval is bypassed, so the trail is mandatory - and the only place naming the clicker.
        auditLogger.record(Action.ROLLBACK_VERSION, TargetType.CV, cvId,
                current.getVersionNumber(),
                new RollbackAuditDetail(source.getVersionNumber(), published.getVersionNumber()));

        eventPublisher.publishEvent(new CvRolledBackEvent(
                cvId,
                requireProfileAnyStatus(cv.getProfileId()).getEmployeeId(),
                CurrentActor.requireUserId(),
                source.getVersionNumber(),
                published.getVersionNumber()
        ));

        // The CV row is still locked, so the replaced version is the new one's predecessor
        return toHistoryItem(published, source.getVersionNumber(), current.getId());
    }

    // ---------- Validation ----------

    private void validateLanguageAvailable(UUID profileId, Language language) {
        if (cvRepository.existsByProfileIdAndLanguageAndLifecycleStatus(
                profileId, language, LifecycleStatus.ACTIVE)) {
            throw new ApiException.BusinessRuleException(ErrorCode.CV_LANGUAGE_EXISTS);
        }
    }

    // Shared by delete and rollback: Both wait until the review ends.
    private void validateNoPendingDraft(UUID cvId) {
        if (cvDraftRepository.existsByCvIdAndStatusIn(cvId, CvDraft.LOCKED_STATUSES)) {
            throw new ApiException.BusinessRuleException(ErrorCode.CV_HAS_PENDING_DRAFTS);
        }
    }

    // Rolling back to the current version would publish an identical copy
    private void validateNotCurrent(CvVersion source, CvVersion current) {
        if (source.getId().equals(current.getId())) {
            throw new ApiException.BusinessRuleException(ErrorCode.ROLLBACK_TO_CURRENT);
        }
    }

    /*
     * The editor sends updatedAt of the draft it loaded, null when none was open. The request
     * carries the whole content, so saving over a draft that changed meanwhile would drop the
     * change. Both directions count: An open draft that moved on and a draft that appeared
     * since the page loaded (structure sync created it).
     *
     * Compared on the row just locked, read back from the database, so seconds-only DATETIME
     * precision is on both sides. A sync landing in the same second the page loaded is not
     * caught - the same limit the department CAS accepts.
     */
    private void verifyDraftToken(CvDraft open, LocalDateTime expected) {
        LocalDateTime actual = open == null ? null : open.getUpdatedAt();
        if (!Objects.equals(actual, expected)) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }
    }

    // ---------- Master handover ----------

    /*
     * Deleting the master leaves every localisation pointing at a deleted anchor, so the caller
     * must name a successor while other ACTIVE CVs remain. When the master is the last CV, there
     * is nothing to re-anchor and no successor is asked for.
     *
     * Read-only: it only picks and validates, so a rejected delete writes nothing.
     */
    private Cv resolveSuccessorMaster(Cv master, UUID newMasterCvId) {
        List<Cv> siblings = cvRepository
                .findByProfileIdAndLifecycleStatusOrderByLanguageAsc(master.getProfileId(), LifecycleStatus.ACTIVE)
                .stream()
                .filter(candidate -> !candidate.getId().equals(master.getId()))
                .toList();

        if (siblings.isEmpty()) {
            return null;
        }
        if (newMasterCvId == null) {
            throw new ApiException.BusinessRuleException(ErrorCode.MUST_DESIGNATE_NEW_MASTER);
        }

        return siblings.stream()
                .filter(candidate -> candidate.getId().equals(newMasterCvId))
                .findFirst()
                .orElseThrow(() -> new ApiException.BusinessRuleException(ErrorCode.INVALID_NEW_MASTER));
    }

    // Runs only after the outgoing master is flushed as DELETED and its master slot is free.
    private void promoteToMaster(Cv previousMaster, Cv successor) {
        successor.setMasterCvId(null);
        // Flushed before the rest are repointed, so the unique index never sees two masters.
        cvRepository.saveAndFlush(successor);

        cvRepository
                .findByProfileIdAndLifecycleStatusOrderByLanguageAsc(successor.getProfileId(), LifecycleStatus.ACTIVE)
                .stream()
                .filter(candidate -> !candidate.getId().equals(successor.getId()))
                .forEach(candidate -> {
                    candidate.setMasterCvId(successor.getId());
                    cvRepository.save(candidate);
                });

        auditLogger.record(Action.UPDATE_CV_DRAFT, TargetType.CV, successor.getId(),
                previousMaster.getId(), successor.getId());
    }

    /*
     * A deleted master keeps master_cv_id null, because deletion never touched the column.
     * Restoring it as-is would produce two masters in one profile, so it is re-anchored to
     * whichever CV holds mastership now - or keeps it if the profile has no ACTIVE CV left.
     */
    private void realignMasterOnRestore(Cv cv, UUID profileId) {
        Optional<Cv> currentMaster = cvRepository
                .findByProfileIdAndMasterCvIdIsNullAndLifecycleStatus(profileId, LifecycleStatus.ACTIVE);

        if (cv.isMaster()) {
            currentMaster.ifPresent(master -> cv.setMasterCvId(master.getId()));
            return;
        }

        // Its old anchor may itself have been deleted meanwhile; re-point rather than dangle.
        boolean anchorStillActive = cvRepository
                .findByIdAndLifecycleStatus(cv.getMasterCvId(), LifecycleStatus.ACTIVE)
                .isPresent();

        if (!anchorStillActive) {
            cv.setMasterCvId(currentMaster.map(Cv::getId).orElse(null));
        }
    }

    // ---------- Draft branch ----------

    private void publishOrDraft(Cv cv, CvContent content, UUID avatarImageId, UUID ownerId) {
        if (publishedDirectly()) {
            versionPublisher.publish(PublishCommand.directEdit(cv.getId(), content, avatarImageId, ownerId));
            return;
        }
        CvDraft draft = new CvDraft();
        draft.setCvId(cv.getId());
        draft.setOwnerId(ownerId);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setReviewRound(0);
        draft.setContentJson(codec.write(content));
        draft.setAvatarImageId(avatarImageId);
        cvDraftRepository.save(draft);
    }

    // The open draft was locked, token-checked and lock-checked by edit()
    private CvDraftResponse writeDraft(Cv cv, CvProfile profile, Optional<CvDraft> open, CvEditRequest request) {
        CvDraft draft = open.orElseGet(() -> fromCurrentVersion(cv, profile.getEmployeeId()));
        CvContent normalised = normalise(request.content());

        /*
         * A CV whose first draft was cancelled before anything was published has no source left for
         * the identity fields. Take them from the account, exactly as create() does - otherwise the
         * restarted draft carries a blank name and never passed the required-section check.
         */
        if (open.isEmpty() && currentVersionOf(cv) == null) {
            normalised = codec.applyPersonalInfoSnapshot(normalised, requireUser(profile.getEmployeeId()));
        }

        itemIdGuard.requireOwnedOrNew(profile.getId(), normalised);

        // Linked while drafting, so the approved version already carries catalogue ids
        normalised = skillService.linkCatalog(normalised);

        draft.setContentJson(codec.write(normalised));
        draft.setAvatarImageId(request.avatarImageId());
        CvDraft saved = persist(draft);

        auditLogger.record(Action.UPDATE_CV_DRAFT, TargetType.CV_DRAFT, saved.getId(), null, saved.getStatus());
        return toDraftResponse(saved);
    }

    /*
     * Unsaved. Content and avatar from the current version; a CV with no version yet starts from
     * the template with name and email taken from the account.
     */
    private CvDraft fromCurrentVersion(Cv cv, UUID ownerId) {
        Optional<CvVersion> current = cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(cv.getId());

        CvDraft draft = new CvDraft();
        draft.setCvId(cv.getId());
        draft.setOwnerId(ownerId);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setReviewRound(0);
        draft.setContentJson(current
                .map(CvVersion::getContentJson)
                .orElseGet(() -> codec.write(codec.seedFrom(requireUser(ownerId)))));
        draft.setAvatarImageId(current.map(CvVersion::getAvatarImageId).orElse(null));
        return draft;
    }

    /*
     * updated_at is stamped at flush and DATETIME keeps whole seconds, so the in-memory value
     * differs from the stored one. Re-reading returns the exact token the next save must send -
     * the same trap DepartmentServiceImpl.persist avoids.
     */
    private CvDraft persist(CvDraft draft) {
        CvDraft saved = cvDraftRepository.saveAndFlush(draft);
        entityManager.refresh(saved);
        return saved;
    }

    // ---------- New CV content ----------

    private Optional<Cv> findActiveMaster(UUID profileId) {
        return cvRepository.findByProfileIdAndMasterCvIdIsNullAndLifecycleStatus(profileId, LifecycleStatus.ACTIVE);
    }

    // The first CV starts from the template; a later language from the master's structure.
    private CvContent newCvContent(Cv master, User owner) {
        return master == null
                ? codec.seedFrom(owner)
                : codec.scaffoldTranslation(structureOf(master), owner);
    }

    // Master's current version; an unpublished master lends its open draft, an empty one the template.
    private CvContent structureOf(Cv master) {
        return cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(master.getId())
                .map(CvVersion::getContentJson)
                .or(() -> cvDraftRepository.findByCvIdAndStatusIn(master.getId(), CvDraft.OPEN_STATUSES)
                        .map(CvDraft::getContentJson))
                .map(codec::read)
                .orElseGet(CvContent::empty);
    }

    // Every content write: Ids filled in first, then started placeholders lose their untranslated flag.
    private CvContent normalise(CvContent content) {
        return codec.normaliseTranslationFlags(codec.normaliseItemIds(content));
    }

    // ---------- Access control ----------

    /*
     * Admin/HR read everything; a tech lead reads members of the teams they lead, resolved through
     * team membership rather than the profile's team; everyone reads their own.
     * Same rule as the CV search scope, so a search result always opens.
     */
    private void requireCanRead(UUID employeeId) {
        Role role = CurrentActor.requireRole();
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        UUID actorId = CurrentActor.requireUserId();
        if (actorId.equals(employeeId)) {
            return;
        }
        if (role == Role.TECH_LEAD && cvSearchRepository.isMemberOfLedTeam(actorId, employeeId)) {
            return;
        }
        // Reads are refused as out of scope; NOT_CV_OWNER would tell a reader they tried to edit
        throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
    }

    private boolean isOwner(UUID employeeId) {
        return CurrentActor.requireUserId().equals(employeeId);
    }

    private void requireOwner(UUID employeeId) {
        if (!isOwner(employeeId)) {
            throw new ApiException.ForbiddenException(ErrorCode.NOT_CV_OWNER);
        }
    }

    private void requireAdminOrHr() {
        Role role = CurrentActor.requireRole();
        if (role != Role.ADMIN && role != Role.HR) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
    }

    // Only an Admin/HR owner skips approval; a Tech Lead owner still goes through it.
    private boolean publishedDirectly() {
        Role role = CurrentActor.requireRole();
        return role == Role.ADMIN || role == Role.HR;
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

    // ---------- Private helpers ----------

    private Cv requireCv(UUID cvId) {
        return cvRepository.findById(cvId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv", cvId));
    }

    private Cv requireActiveCv(UUID cvId) {
        return cvRepository.findByIdAndLifecycleStatus(cvId, LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv", cvId));
    }

    private Cv requireActiveCvForUpdate(UUID cvId) {
        Cv cv = cvRepository.findByIdForUpdate(cvId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv", cvId));
        if (cv.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new ApiException.NotFoundException("cv", cvId);
        }
        return cv;
    }

    private CvProfile requireActiveProfile(UUID profileId) {
        return cvProfileRepository.findByIdAndLifecycleStatus(profileId, LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", profileId));
    }

    private CvProfile requireProfileAnyStatus(UUID profileId) {
        return cvProfileRepository.findById(profileId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", profileId));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException.NotFoundException("user", userId));
    }

    private CvVersion currentVersionOf(Cv cv) {
        return cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(cv.getId()).orElse(null);
    }

    // Stored content with skill names taken from the catalogue, as every screen and PDF shows it.
    private CvContent readForDisplay(String contentJson) {
        return skillService.applyCatalogNames(codec.read(contentJson));
    }

    /*
     * CANCELLED is terminal, so updated_by / updated_at of that row are the canceller and the moment
     * for good - no separate columns needed. A draft the owner discarded themselves yields nothing.
     */
    private DraftCancellationResponse lastCancellationOf(UUID cvId, UUID ownerId) {
        return cvDraftRepository.findTopByCvIdOrderByCreatedAtDesc(cvId)
                .filter(draft -> draft.getStatus() == DraftStatus.CANCELLED)
                .filter(draft -> !ownerId.equals(draft.getUpdatedBy()))
                .map(draft -> new DraftCancellationResponse(
                        draft.getCancellationReason(),
                        resolveEmployeeName(draft.getUpdatedBy()),
                        draft.getUpdatedAt()
                ))
                .orElse(null);
    }

    private String resolveEmployeeName(UUID employeeId) {
        return userRepository.findById(employeeId).map(User::getFullName).orElse(null);
    }

    private Map<UUID, CvProfile> loadProfiles(List<Cv> cvs) {
        Set<UUID> ids = cvs.stream().map(Cv::getProfileId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : cvProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(CvProfile::getId, Function.identity()));
    }

    private Map<UUID, String> loadEmployeeNames(Collection<CvProfile> profiles) {
        Set<UUID> ids = profiles.stream().map(CvProfile::getEmployeeId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private CvResponse toResponse(Cv cv, CvProfile profile, String employeeName, CvVersion current) {
        CvDraft openDraft = cvDraftRepository
                .findByCvIdAndStatusIn(cv.getId(), CvDraft.OPEN_STATUSES)
                .orElse(null);

        return new CvResponse(
                cv.getId(),
                cv.getProfileId(),
                profile == null ? null : profile.getName(),
                profile == null ? null : profile.getEmployeeId(),
                employeeName,
                cv.getLanguage(),
                cv.isMaster(),
                cv.getMasterCvId(),
                cv.getLifecycleStatus(),
                current == null ? null : current.getVersionNumber(),
                current == null ? null : current.getPublishedAt(),
                openDraft == null ? null : openDraft.getStatus(),
                untranslatedItemCountOf(openDraft, current),
                cv.getDeletedBy(),
                cv.getDeletedBy() == null ? null : resolveEmployeeName(cv.getDeletedBy()),
                cv.getDeletedAt(),
                cv.getCreatedAt(),
                cv.getUpdatedAt()
        );
    }

    // Open draft first, current version second; one parse per row, rows are few.
    private long untranslatedItemCountOf(CvDraft openDraft, CvVersion current) {
        String contentJson = openDraft != null
                ? openDraft.getContentJson()
                : current == null ? null : current.getContentJson();
        return contentJson == null ? 0 : codec.read(contentJson).untranslatedItemCount();
    }

    // ---------- Version helpers ----------

    private CvVersion requireVersionOf(UUID cvId, UUID versionId) {
        return cvVersionRepository.findByIdAndCvId(versionId, cvId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv version", versionId));
    }

    private CvVersion requireCurrentVersion(UUID cvId) {
        return cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(cvId)
                .orElseThrow(() -> new ApiException.NotFoundException("cv version", cvId));
    }

    private VersionDiffSide toDiffSide(CvVersion version) {
        return new VersionDiffSide(
                version.getVersionNumber(),
                version.getPublishedAt(),
                version.getSource(),
                avatarUrlResolver.resolve(version.getAvatarImageId())
        );
    }

    // The maps are HashMaps, so a null key (skipped level, no rollback, v1) simply reads as null.
    private CvVersionHistoryItem toHistoryItem(CvVersionRef row,
                                               Map<UUID, String> names,
                                               Map<UUID, Integer> rollbackSources,
                                               Map<Integer, UUID> predecessors) {
        return new CvVersionHistoryItem(
                row.getId(),
                row.getVersionNumber(),
                row.getPublishedAt(),
                row.getSource(),
                names.get(row.getAuthoredBy()),
                names.get(row.getLevel1ApproverId()),
                names.get(row.getLevel2ApproverId()),
                rollbackSources.get(row.getRollbackSourceVersionId()),
                predecessors.get(row.getVersionNumber() - 1)
        );
    }

    // A version just published: Source number and predecessor are already known to the caller
    private CvVersionHistoryItem toHistoryItem(CvVersion version, Integer rollbackSourceNumber, UUID previousVersionId) {
        // Arrays.asList keeps the null ids, which loadUserNames then skips
        Map<UUID, String> names = loadUserNames(Arrays.asList(
                version.getAuthoredBy(), version.getLevel1ApproverId(), version.getLevel2ApproverId()
        ));

        return new CvVersionHistoryItem(
                version.getId(),
                version.getVersionNumber(),
                version.getPublishedAt(),
                version.getSource(),
                names.get(version.getAuthoredBy()),
                names.get(version.getLevel1ApproverId()),
                names.get(version.getLevel2ApproverId()),
                rollbackSourceNumber,
                previousVersionId
        );
    }

    // id -> full name in one query; nulls are skipped.
    private Map<UUID, String> loadUserNames(Collection<UUID> userIds) {
        Set<UUID> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), user.getFullName()));
        }
        return names;
    }

    private Map<UUID, Integer> loadRollbackSourceNumbers(List<CvVersionRef> rows) {
        Set<UUID> ids = rows.stream()
                .map(CvVersionRef::getRollbackSourceVersionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Integer> numbers = new HashMap<>();
        if (!ids.isEmpty()) {
            cvVersionRepository.findByIdIn(ids).forEach(ref -> numbers.put(ref.getId(), ref.getVersionNumber()));
        }
        return numbers;
    }

    // version_number -> id of v_{n-1} for every row of the page; v1 has none.
    private Map<Integer, UUID> loadPredecessorIds(UUID cvId, List<CvVersionRef> rows) {
        Set<Integer> numbers = rows.stream()
                .map(row -> row.getVersionNumber() - 1)
                .filter(number -> number > 0)
                .collect(Collectors.toSet());
        Map<Integer, UUID> ids = new HashMap<>();
        if (!numbers.isEmpty()) {
            cvVersionRepository.findByCvIdAndVersionNumberIn(cvId, numbers)
                    .forEach(ref -> ids.put(ref.getVersionNumber(), ref.getId()));
        }
        return ids;
    }

    private CvVersionSummary toSummary(CvVersion version) {
        return new CvVersionSummary(
                version.getId(),
                version.getVersionNumber(),
                version.getPublishedAt(),
                version.getSource(),
                version.getAuthoredBy(),
                version.getLevel1ApproverId(),
                version.getLevel2ApproverId()
        );
    }

    private CvDraftResponse toDraftResponse(CvDraft draft) {
        CvContent content = readForDisplay(draft.getContentJson());

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
                content.deletedInMasterCount(),
                draft.getSubmittedAt(),
                draft.getUpdatedAt(),
                forDraft(draft.getId())
        );
    }
}

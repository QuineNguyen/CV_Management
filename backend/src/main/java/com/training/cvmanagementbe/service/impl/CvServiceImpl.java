package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.cvs.CvCreateRequest;
import com.training.cvmanagementbe.dto.request.cvs.CvDeleteRequest;
import com.training.cvmanagementbe.dto.request.cvs.CvEditRequest;
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
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.CvContent;
import com.training.cvmanagementbe.record.PublishCommand;
import com.training.cvmanagementbe.repository.*;
import com.training.cvmanagementbe.service.CvService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final VersionPublisher versionPublisher;
    private final CvContentCodec codec;
    private final CvItemIdGuard itemIdGuard;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AuditLogger auditLogger;

    // ---------- Queries ----------

    @Override
    public CvDetailResponse getById(UUID cvId) {
        Cv cv = requireCv(cvId);
        CvProfile profile = requireProfileAnyStatus(cv.getProfileId());
        requireCanRead(profile.getEmployeeId());

        Optional<CvVersion> current = cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(cvId);

        // The open draft rides along: the edit screen needs it and a second round trip for a
        // row we already know exists buys nothing.
        CvDraftResponse openDraft = cvDraftRepository
                .findByCvIdAndStatusIn(cvId, CvDraft.OPEN_STATUSES)
                .map(this::toDraftResponse)
                .orElse(null);

        UUID avatarImageId = current.map(CvVersion::getAvatarImageId).orElse(null);

        return new CvDetailResponse(
                toResponse(cv, profile, resolveEmployeeName(profile.getEmployeeId()), current.orElse(null)),
                current.map(this::toSummary).orElse(null),
                current.map(version -> codec.read(version.getContentJson())).orElse(null),
                avatarImageId,
                avatarUrlResolver.resolve(avatarImageId),
                openDraft
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

    @Override
    public List<CvVersionSummary> listVersions(UUID cvId) {
        Cv cv = requireCv(cvId);
        requireCanRead(requireProfileAnyStatus(cv.getProfileId()).getEmployeeId());

        return cvVersionRepository.findByCvIdOrderByVersionNumberDesc(cvId).stream()
                .map(this::toSummary)
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

        Cv cv = new Cv();
        cv.setProfileId(profileId);
        cv.setLanguage(request.language());
        cv.setLifecycleStatus(LifecycleStatus.ACTIVE);
        // First CV of a profile is its master; every later one hangs off the current master.
        cv.setMasterCvId(cvRepository
                .findByProfileIdAndMasterCvIdIsNullAndLifecycleStatus(profileId, LifecycleStatus.ACTIVE)
                .map(Cv::getId)
                .orElse(null));

        Cv saved = cvRepository.saveAndFlush(cv);

        // Identity fields always come from the account, whatever the client sent.
        // Item ids are filled in before anything downstream tries to match on them.
        CvContent content = codec.normaliseItemIds(request.content() == null
                ? codec.seedFrom(owner)
                : codec.applyPersonalInfoSnapshot(request.content(), owner));

        // Checked after ids are filled in, so a generated id is validated like any other.
        itemIdGuard.requireOwnedOrNew(profileId, content);

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
        Cv cv = requireActiveCv(cvId);
        CvProfile profile = requireActiveProfile(cv.getProfileId());

        // No path exists for anyone but the owner to write CV content.
        if (!CurrentActor.requireUserId().equals(profile.getEmployeeId())) {
            throw new ApiException.ForbiddenException(ErrorCode.NOT_CV_OWNER);
        }

        if (publishedDirectly()) {
            CvContent content = codec.normaliseItemIds(request.content());
            itemIdGuard.requireOwnedOrNew(profile.getId(), content);

            CvVersion version = versionPublisher.publish(PublishCommand.directEdit(
                    cv.getId(),
                    codec.normaliseItemIds(request.content()),
                    request.avatarImageId(),
                    profile.getEmployeeId()
            ));

            // Skipping the approval flow is exactly the case that must leave a trail.
            auditLogger.record(Action.DIRECT_EDIT_PUBLISH, TargetType.CV, cv.getId(),
                    null, version.getVersionNumber());

            return CvEditResponse.ofVersion(toSummary(version));
        }

        return CvEditResponse.ofDraft(writeDraft(cv, profile, request));
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

        // A request pointing at a deleted CV can never be answered, so it stops asking.
        cvRepository.cancelPendingRequestsByCvId(cvId, actorId, deletedAt);

        auditLogger.record(Action.DELETE_CV, TargetType.CV, cvId, before, null);
        // TODO [Phase 4]: send email and in-app notification to the owner
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

        // TODO [Phase 4]: send email and in-app notification to the owner
        return after;
    }

    // ---------- Validation ----------

    private void validateLanguageAvailable(UUID profileId, Language language) {
        if (cvRepository.existsByProfileIdAndLanguageAndLifecycleStatus(
                profileId, language, LifecycleStatus.ACTIVE)) {
            throw new ApiException.BusinessRuleException(ErrorCode.CV_LANGUAGE_EXISTS);
        }
    }

    private void validateNoPendingDraft(UUID cvId) {
        if (cvDraftRepository.existsByCvIdAndStatusIn(cvId, CvDraft.LOCKED_STATUSES)) {
            throw new ApiException.BusinessRuleException(ErrorCode.CV_HAS_PENDING_DRAFTS);
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

    private CvDraftResponse writeDraft(Cv cv, CvProfile profile, CvEditRequest request) {
        CvDraft draft = cvDraftRepository.findByCvIdAndStatusIn(cv.getId(), CvDraft.OPEN_STATUSES)
                .orElseGet(() -> newDraftFromCurrentVersion(cv, profile));

        if (draft.isContentLocked()) {
            throw new ApiException.BusinessRuleException(ErrorCode.DRAFT_CONTENT_LOCKED);
        }

        CvContent normalised = codec.normaliseItemIds(request.content());
        itemIdGuard.requireOwnedOrNew(profile.getId(), normalised);

        draft.setContentJson(codec.write(normalised));
        draft.setAvatarImageId(request.avatarImageId());
        CvDraft saved = cvDraftRepository.save(draft);

        auditLogger.record(Action.UPDATE_CV_DRAFT, TargetType.CV_DRAFT, saved.getId(), null, saved.getStatus());
        return toDraftResponse(saved);
    }

    // A fresh draft starts from the published content or the black template if none exists.
    private CvDraft newDraftFromCurrentVersion(Cv cv, CvProfile profile) {
        Optional<CvVersion> current = cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(cv.getId());

        CvDraft draft = new CvDraft();
        draft.setCvId(cv.getId());
        draft.setOwnerId(profile.getEmployeeId());
        draft.setStatus(DraftStatus.DRAFT);
        draft.setReviewRound(0);
        draft.setContentJson(current
                .map(CvVersion::getContentJson)
                .orElseGet(() -> codec.write(codec.seedFrom(requireUser(profile.getEmployeeId())))));
        draft.setAvatarImageId(current.map(CvVersion::getAvatarImageId).orElse(null));
        return draft;
    }

    // ---------- Access control ----------

    // Admin/HR read everything; everyone else reads only their own. Tech Lead scope: Phase 3.
    private void requireCanRead(UUID employeeId) {
        Role role = CurrentActor.requireRole();
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        requireOwner(employeeId);
    }

    private void requireOwner(UUID employeeId) {
        if (!CurrentActor.requireUserId().equals(employeeId)) {
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
        DraftStatus draftStatus = cvDraftRepository
                .findByCvIdAndStatusIn(cv.getId(), CvDraft.OPEN_STATUSES)
                .map(CvDraft::getStatus)
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
                draftStatus,
                cv.getDeletedBy(),
                cv.getDeletedBy() == null ? null : resolveEmployeeName(cv.getDeletedBy()),
                cv.getDeletedAt(),
                cv.getCreatedAt(),
                cv.getUpdatedAt()
        );
    }

    private CvVersionSummary toSummary(CvVersion version) {
        return new CvVersionSummary(
                version.getId(),
                version.getVersionNumber(),
                version.getPublishedAt(),
                version.getSource(),
                version.getAuthoredBy(),
                version.getLevel1ApproverId(),
                version.getLevel2ApproverId(),
                version.getChangeSummary()
        );
    }

    private CvDraftResponse toDraftResponse(CvDraft draft) {
        CvContent content = codec.read(draft.getContentJson());

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
}

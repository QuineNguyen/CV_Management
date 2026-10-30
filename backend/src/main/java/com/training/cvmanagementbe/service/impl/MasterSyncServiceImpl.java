package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.approvals.AssignmentStatus;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;
import com.training.cvmanagementbe.enums.users.AccountStatus;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.record.cvs.MasterStructureDiff;
import com.training.cvmanagementbe.record.cvs.StructureSyncResult;
import com.training.cvmanagementbe.record.events.CvStructureSyncedEvent;
import com.training.cvmanagementbe.repository.*;
import com.training.cvmanagementbe.service.MasterSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * Structure sync from the master to the other languages of its profile.
 *
 * - Writes drafts only, never a version: A version comes from three sources and "the system"
 * is not one of them. The new structure becomes official when the owner submits and
 * the draft passes both review levels.
 * - Runs in the master's publishing transaction (MANDATORY). A sync that fails rolls the master
 * version back too: better a failed publishing than a new master the other languages silently miss.
 * - Lock order is cvs(master) -> cvs(target, language order) -> cv_drafts(target), the same
 * cvs-before-drafts order as the owner's save and the level-2 approval, so nothing deadlocks.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MasterSyncServiceImpl implements MasterSyncService {

    private final CvRepository cvRepository;
    private final CvDraftRepository cvDraftRepository;
    private final CvProfileRepository cvProfileRepository;
    private final CvVersionRepository cvVersionRepository;
    private final UserRepository userRepository;
    private final ApprovalAssignmentRepository assignmentRepository;
    private final CvContentCodec codec;
    private final CvItemIdGuard itemIdGuard;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void onMasterPublished(Cv master, CvContent previous, CvContent current) {
        MasterStructureDiff diff = codec.structureDiff(previous, current);

        // Field edits only (a spelling fix): No draft anywhere changes
        if (diff.isEmpty()) {
            return;
        }

        CvProfile profile = cvProfileRepository.findById(master.getProfileId())
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", master.getProfileId()));
        boolean ownerActive = isActive(profile.getEmployeeId());

        // Same profile only
        List<Cv> targets = cvRepository
                .findByProfileIdAndLifecycleStatusOrderByLanguageAsc(profile.getId(), LifecycleStatus.ACTIVE)
                .stream()
                .filter(cv -> !cv.getId().equals(master.getId()))
                .toList();

        for (Cv target : targets) {
            syncTarget(target, profile, ownerActive, diff);
        }
    }

    // ---------- One target ----------

    /*
     * Which draft receives the change:
     * - DRAFT / REJECTED: That draft;
     * - PENDING_*: That draft too, the one exception to the content lock;
     * - None open: A new DRAFT of the owner, copied from the current version.
     */
    private void syncTarget(Cv target, CvProfile profile, boolean ownerActive, MasterStructureDiff diff) {
        // cvs first; re-checked because a delete may have commited while we waited
        boolean stillActive = cvRepository.findByIdForUpdate(target.getId())
                .map(cv -> cv.getLifecycleStatus() == LifecycleStatus.ACTIVE)
                .orElse(false);
        if (!stillActive) {
            return;
        }

        // Locking read: Also sees a draft the owner created while this transaction waited
        Optional<CvDraft> open = cvDraftRepository
                .findByCvIdAndStatusInForUpdate(target.getId(), CvDraft.OPEN_STATUSES);

        // Nobody could ever submit a draft created for a deactivated owner
        if (open.isEmpty() && !ownerActive) {
            return;
        }

        CvDraft draft = open.orElseGet(() -> fromCurrentVersion(target, profile.getEmployeeId()));
        DraftStatus statusBefore = open.map(CvDraft::getStatus).orElse(null);

        // Apply first, decide after: No real change means no write, no new draft, no notice
        StructureSyncResult result = codec.applyStructureSync(codec.read(draft.getContentJson()), diff);
        if (!result.changed()) {
            return;
        }

        CvContent synced = codec.normaliseItemIds(result.content());
        // Ids come from the master of this profile; checked anyway so the rule lives in one place
        itemIdGuard.requireOwnedOrNew(profile.getId(), synced);

        draft.applyStructureSync(codec.write(synced));
        CvDraft saved = cvDraftRepository.save(draft);

        // Old value: The draft's status, null when sync created it; new value: What changed
        auditLogger.record(Action.SYNC_CV_STRUCTURE, TargetType.CV_DRAFT, saved.getId(), statusBefore, result);

        eventPublisher.publishEvent(new CvStructureSyncedEvent(
                target.getId(),
                saved.getId(),
                saved.getOwnerId(),
                target.getLanguage(),
                synced.untranslatedItemCount(),
                synced.deletedInMasterCount(),
                reviewerOf(saved)
        ));
    }

    // ---------- Helpers ----------

    // Only a draft under review has someone else reading it right now
    private UUID reviewerOf(CvDraft draft) {
        if (!draft.isContentLocked()) {
            return null;
        }
        return assignmentRepository.findByDraftIdAndStatus(draft.getId(), AssignmentStatus.ASSIGNED)
                .map(ApprovalAssignment::getAssigneeId)
                .orElse(null);
    }

    /*
     * Unsaved. Content and avatar from the current version; a CV with no version yet starts from
     * the template with name and email taken from the account.
     */
    public CvDraft fromCurrentVersion(Cv cv, UUID ownerId) {
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

    private boolean isActive(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == AccountStatus.ACTIVE)
                .orElse(false);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException.NotFoundException("user", userId));
    }
}

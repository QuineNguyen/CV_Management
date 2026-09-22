package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.cvs.ChangeType;
import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.CvContent;
import com.training.cvmanagementbe.record.PublishCommand;
import com.training.cvmanagementbe.repository.ChangeLogEntryRepository;
import com.training.cvmanagementbe.repository.CvDraftRepository;
import com.training.cvmanagementbe.repository.CvRepository;
import com.training.cvmanagementbe.repository.CvVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
 * The single writer of cv_versions.
 *
 * - All three sources go through here - APPROVAL, DIRECT_EDIT, ROLLBACK
 * because the invariants around publishing are the same regardless of who
 * triggered it: the number is MAX+1, the change log is generated, pending update requests close
 * and the draft that produced it (if any) reaches its terminal state. Duplicating that logic per
 * caller is how three of the four end up subtly different.
 *
 * - Requires an existing transaction: a published version and the state changes that accompany it
 * must commit or roll back together.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class VersionPublisher {

    private final CvRepository cvRepository;
    private final CvVersionRepository cvVersionRepository;
    private final CvDraftRepository cvDraftRepository;
    private final ChangeLogEntryRepository changeLogEntryRepository;
    private final ChangeLogGenerator changeLogGenerator;
    private final CvContentCodec codec;
    private final AuditLogger auditLogger;

    /*
     * Publishes one immutable version.
     *
     * - Steps, in one transaction:
     *    * Lock the CV row
     *    * version_number = MAX(existing) + 1
     *    * Insert cv_versions row; UNIQUE(cv_id, version_number) is the final safety net
     *    * Generate change_log_entries by diffing against the previous version
     *    * Close every PENDING update request of this CV as COMPLETED
     *    * If a draft produced it: mark PUBLISHED and record published_version_id
     *    * Audit log
     */
    public CvVersion publish(PublishCommand command) {
        Cv cv = cvRepository.findByIdForUpdate(command.cvId())
                .orElseThrow(() -> new ApiException.NotFoundException("cv", command.cvId()));

        if (cv.getLifecycleStatus() != LifecycleStatus.ACTIVE) {
            throw new ApiException.NotFoundException("cv", command.cvId());
        }

        Optional<CvVersion> previous = cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(cv.getId());

        CvVersion version = new CvVersion();
        version.setCvId(cv.getId());
        version.setVersionNumber(cvVersionRepository.findMaxVersionNumber(cv.getId()) + 1);
        version.setContentJson(codec.write(command.content()));
        version.setContentText(codec.flattenText(command.content()));
        version.setAvatarImageId(command.avatarImageId());
        version.setAuthoredBy(command.authoredBy());
        version.setLevel1ApproverId(command.level1ApproverId());
        version.setLevel2ApproverId(command.level2ApproverId());
        version.setPublishedAt(LocalDateTime.now());
        version.setSource(command.source());
        version.setRollbackSourceVersionId(command.rollbackSourceVersionId());

        CvVersion saved = cvVersionRepository.saveAndFlush(version);

        saved.setChangeSummary(summarise(buildChangeLog(command, previous, saved)));
        cvVersionRepository.saveAndFlush(saved);

        // Creating the CV does not complete a request; publishing content does.
        cvRepository.completePendingRequestsByCvId(cv.getId(), CurrentActor.requireUserId(), saved.getPublishedAt());

        closeDraft(command, saved);

        // TODO [Phase 7]: Trigger MasterSyncService when the published CV is the profile master.
        auditLogger.record(Action.PUBLISH_CV_VERSION, TargetType.CV_VERSION, saved.getId(),
                previous.map(CvVersion::getVersionNumber).orElse(null), saved.getVersionNumber());

        return saved;
    }

    // ---------- Private helpers ----------

    private List<ChangeLogEntry> buildChangeLog(PublishCommand command,
                                                Optional<CvVersion> previous,
                                                CvVersion saved) {
        CvContent previousContent = previous.map(version -> codec.read(version.getContentJson())).orElse(null);

        List<ChangeLogEntry> changes = changeLogGenerator.generate(
                previousContent,
                command.content(),
                saved.getId(),
                previous.map(CvVersion::getAvatarImageId).orElse(null),
                command.avatarImageId()
        );

        return changeLogEntryRepository.saveAll(changes);
    }

    private void closeDraft(PublishCommand command, CvVersion saved) {
        if (command.draftId() == null) {
            return;
        }
        CvDraft draft = cvDraftRepository.findById(command.draftId())
                .orElseThrow(() -> new ApiException.NotFoundException("cv draft", command.draftId()));

        draft.setStatus(DraftStatus.PUBLISHED);
        draft.setPublishedVersionId(saved.getId());
        cvDraftRepository.save(draft);
    }

    // Short human-readable roll-up, stored on the version and shown in the history list.
    private String summarise(List<ChangeLogEntry> changes) {
        if (changes.isEmpty()) {
            return "No content changes";
        }
        Map<ChangeType, Integer> counts = new EnumMap<>(ChangeType.class);
        changes.forEach(entry -> counts.merge(entry.getChangeType(), 1, Integer::sum));

        StringBuilder builder = new StringBuilder();
        counts.forEach((type, count) -> {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(count).append(' ').append(type.name().toLowerCase());
        });
        return builder.toString();
    }
}

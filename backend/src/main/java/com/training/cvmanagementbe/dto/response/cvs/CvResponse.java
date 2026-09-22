package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * List-level view of a CV.
 *
 * - master: Derived from master_cv_id being null.
 * - currentVersionNumber: Null while the CV has no published version yet.
 * - openDraftStatus: Null when no draft is open; drives the edit screen's branching.
 */
public record CvResponse(
        UUID id,
        UUID profileId,
        String profileName,
        UUID employeeId,
        String employeeName,
        Language language,
        boolean master,
        UUID masterCvId,
        LifecycleStatus lifecycleStatus,
        Integer currentVersionNumber,
        LocalDateTime currentVersionPublishedAt,
        DraftStatus openDraftStatus,
        UUID deletedBy,
        String deletedByName,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

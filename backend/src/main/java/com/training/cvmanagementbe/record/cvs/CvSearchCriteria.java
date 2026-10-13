package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.enums.cvs.CvReadScope;
import com.training.cvmanagementbe.enums.cvs.Language;

import java.util.List;
import java.util.UUID;

/*
 * Resolved search input handed from the service to the repository: Scope already decided,
 * department expanded to its subtree, keyword split into terms. Lists are never null.
 */
public record CvSearchCriteria(
        CvReadScope scope,
        UUID actorId,
        String employeeName,
        String profileName,
        boolean primaryOnly,
        List<UUID> departmentIds,
        UUID teamId,
        Language language,
        List<String> contentTerms,
        List<UUID> skillIds
) {

    // Skills and full text read the latest published version only
    public boolean hasContentCriteria() {
        return !contentTerms.isEmpty() || !skillIds.isEmpty();
    }
}

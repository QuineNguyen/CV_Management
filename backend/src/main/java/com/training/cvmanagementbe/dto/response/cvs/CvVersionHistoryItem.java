package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.VersionSource;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One row of the version timeline. Names instead of user ids - the screen shows people.
 * For ROLLBACK, author and both reviewers are inherited from the source version.
 */
public record CvVersionHistoryItem(
        UUID id,
        int versionNumber,
        LocalDateTime publishedAt,
        VersionSource source,
        String authoredByName,
        String level1ApproverName,
        String level2ApproverName,
        // Set only for ROLLBACK
        Integer rollbackSourceVersionNumber,
        // v_{n-1}; null for v1. Lets the client compare a row with its predecessor directly.
        UUID previousVersionId
) {
}

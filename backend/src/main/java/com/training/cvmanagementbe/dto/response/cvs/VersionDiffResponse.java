package com.training.cvmanagementbe.dto.response.cvs;

import java.util.List;

/*
 * Result of GET /cvs/{id}/versions/diff.
 * - fromVersion is null when "to" is compared with an empty CV.
 * - sections always holds the nine sections in template order.
 */
public record VersionDiffResponse(
        VersionDiffSide fromVersion,
        VersionDiffSide toVersion,
        DiffStats stats,
        List<SectionDiff> sections
) {
}

package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.VersionSource;

import java.time.LocalDateTime;
import java.util.UUID;

public record CvVersionSummary(
        UUID id,
        int versionNumber,
        LocalDateTime publishedAt,
        VersionSource source,
        UUID authoredBy,
        UUID level1ApproverId,
        UUID level2ApproverId,
        String changeSummary
) {
}

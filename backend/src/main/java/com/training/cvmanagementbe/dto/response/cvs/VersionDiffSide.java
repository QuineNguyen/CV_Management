package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.VersionSource;

import java.time.LocalDateTime;

// Column header of one side of the diff; avatarUrl is a presigned URL, null when no photo.
public record VersionDiffSide(
        int versionNumber,
        LocalDateTime publishedAt,
        VersionSource source,
        String avatarUrl
) {
}

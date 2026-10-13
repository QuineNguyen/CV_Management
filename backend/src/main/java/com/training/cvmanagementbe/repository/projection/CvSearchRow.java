package com.training.cvmanagementbe.repository.projection;

import com.training.cvmanagementbe.enums.cvs.Language;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// One CV of a search page; version fields are null when nothing was ever published
public record CvSearchRow(
        UUID cvId,
        Language language,
        String profileName,
        boolean primaryProfile,
        UUID employeeId,
        String employeeName,
        String employeeEmail,
        UUID avatarImageId,
        String departmentCode,
        String departmentName,
        Integer versionNumber,
        LocalDateTime publishedAt,
        List<UUID> skillIds
) {
}

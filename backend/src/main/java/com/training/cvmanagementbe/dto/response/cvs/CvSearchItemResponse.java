package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.Language;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/*
 * One search result. A CV with no published version still appears for organization criteria;
 * hasPublishedVersion = false is what the "No official version yet" label reads.
 */
@Schema(name = "CvSearchItemResponse", description = "One search result. A CV with no published version still appears for organization criteria; hasPublishedVersion = false is what the 'No official version yet' label reads.")
public record CvSearchItemResponse(
        UUID cvId,
        // Ownership check and stable avatar colour on the client
        UUID employeeId,
        String employeeName,
        String employeeEmail,
        String avatarUrl,
        String profileName,
        boolean primaryProfile,
        String departmentCode,
        String departmentName,
        List<CvSearchTeamTag> teams,
        Language language,
        boolean hasPublishedVersion,
        Integer currentVersionNumber,
        LocalDateTime publishedAt,
        // Catalogue skills of the latest published version, in CV order
        List<CvSearchSkillTag> skills
) {
}

package com.training.cvmanagementbe.dto.request.cvs;

import com.training.cvmanagementbe.enums.cvs.CvUpdateStatus;
import com.training.cvmanagementbe.enums.cvs.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/*
 * Query parameters of GET /cvs. Every field is optional.
 * - Organization criteria: employeeName, profileName, departmentId, teamId, language, updateStatus.
 * - Content criteria: keyword, skillIds - latest published version only.
 * Paging and sorting stay separate @RequestParams, as on every list endpoint.
 */
@Schema(name = "CvSearchFilterRequest", description = "Query parameters of GET /cvs. Every field is optional")
public record CvSearchFilterRequest(

        @Size(max = 100, message = "Employee name must be at most 100 characters")
        String employeeName,

        // Profiles are per employee, so they are matched by name across the company
        @Size(max = 100, message = "Profile name must be at most 100 characters")
        String profileName,

        // Includes every sub-department of the chosen one
        UUID departmentId,

        UUID teamId,

        Language language,

        CvUpdateStatus updateStatus,

        // false or absent: primary profiles only
        Boolean allProfiles,

        @Size(max = 200, message = "Search text must be at most 200 characters")
        String keyword,

        // A CV must hold every selected skill
        @Size(max = 10, message = "Select at most 10 skills")
        List<UUID> skillIds
) {
}

package com.training.cvmanagementbe.dto.response.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

// A team of the CV owner, primary team first
@Schema(name = "CvSearchTeamTag", description = "A team of the CV owner, primary team first")
public record CvSearchTeamTag(
        String code,
        String name,
        boolean primary
) {
}

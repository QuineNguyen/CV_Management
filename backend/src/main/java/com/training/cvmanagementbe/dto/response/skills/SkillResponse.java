package com.training.cvmanagementbe.dto.response.skills;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

// One row of the catalogue screen; cvCount uses the same scope as the CV search skill filter
@Schema(name = "SkillResponse", description = "One row of the catalogue screen; cvCount uses the same scope as the CV search skill filter")
public record SkillResponse(
        UUID id,
        String code,
        String name,
        int displayOrder,
        String createdByName,
        LocalDateTime createdAt,
        long cvCount
) {
}

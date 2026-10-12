package com.training.cvmanagementbe.dto.response.skills;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// Lightweight entry for pickers and filters
@Schema(name = "SkillOptionResponse", description = "Lightweight entry for pickers and filters")
public record SkillOptionResponse(
        UUID id,
        String code,
        String name
) {
}

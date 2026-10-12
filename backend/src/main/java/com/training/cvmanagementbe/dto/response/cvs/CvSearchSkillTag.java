package com.training.cvmanagementbe.dto.response.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// ID lets the client highlight skills that match the active filter
@Schema(name = "CvSearchSkillTag", description = "ID lets the client highlight skills that match the active filter")
public record CvSearchSkillTag(UUID id, String name) {
}

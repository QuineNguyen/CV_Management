package com.training.cvmanagementbe.dto.request.skills;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Zero-based position in catalogue order (display order, then name); past the end means last
@Schema(name = "MoveSkillRequest", description = "Request body for moving a skill in the catalogue order")
public record MoveSkillRequest(
        @NotNull
        @Min(0)
        Integer targetIndex
) {
}

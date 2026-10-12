package com.training.cvmanagementbe.dto.request.skills;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

// Body of both create and update - the three fields Admin/HR may set
@Schema(name = "SkillRequest", description = "Body of both create and update - the three fields Admin/HR may set")
public record SkillRequest(

        @NotBlank(message = "Code is required")
        @Size(max = 50, message = "Code must be at most 50 characters")
        @Pattern(regexp = "^[A-Z0-9]+(_[A-Z0-9]+)*$",
                message = "Code must be UPPER_SNAKE_CASE, e.g. SPRING_BOOT")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @NotNull(message = "Display order is required")
        @PositiveOrZero(message = "Display order cannot be negative")
        @Max(value = 1_000_000, message = "Display order is too large")
        Integer displayOrder
) {
}

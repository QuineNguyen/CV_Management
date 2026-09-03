package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Create/update payload for a CV profile. The primary flag is not settable here - see set-primary.
@Schema(name = "CvProfileRequest", description = "Create/update payload for a CV profile")
public record CvProfileRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 1000)
        String description,

        @NotNull
        UUID linkedTeamId
) {
}

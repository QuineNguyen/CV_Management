package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Payload for creating or updating a team
@Schema(name = "TeamRequest", description = "Payload for creating or updating a team")
public record TeamRequest(

        @NotBlank
        @Size(max = 50)
        String code,

        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 2000)
        String description,

        // Must reference an ACTIVE user whose role is TECH_LEAD
        @NotNull
        UUID techLeadId,

        Integer displayOrder
) {
}

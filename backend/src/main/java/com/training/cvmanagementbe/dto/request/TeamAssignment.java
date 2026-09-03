package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// One team membership assigned to a user. Exactly one entry must be primary
@Schema(name = "TeamAssignment", description = "One team membership assigned to a user. Exactly one entry must be primary")
public record TeamAssignment(

        @NotNull
        UUID teamId,

        boolean primary
) {
}

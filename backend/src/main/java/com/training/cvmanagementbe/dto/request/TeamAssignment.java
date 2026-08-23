package com.training.cvmanagementbe.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// One team membership assigned to a user. Exactly one entry must be primary
public record TeamAssignment(

        @NotNull
        UUID teamId,

        boolean primary
) {
}

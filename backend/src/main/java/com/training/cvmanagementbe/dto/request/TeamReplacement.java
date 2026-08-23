package com.training.cvmanagementbe.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Hands a team over to another tech lead when the current one is deactivated.
public record TeamReplacement(

        @NotNull
        UUID teamId,

        @NotNull
        UUID replacementTechLeadId
) {
}

package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;

// Replacements are mandatory when the target user still leads at least one team
@Schema(name = "DeactivateUserRequest", description = "Payload for deactivating a user, including mandatory team lead replacements")
public record DeactivateUserRequest(

        @Valid
        List<TeamReplacement> replacements
) {
}

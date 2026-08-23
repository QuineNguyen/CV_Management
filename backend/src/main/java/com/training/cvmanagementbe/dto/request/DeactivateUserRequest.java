package com.training.cvmanagementbe.dto.request;

import jakarta.validation.Valid;

import java.util.List;

// Replacements are mandatory when the target user still leads at least one team
public record DeactivateUserRequest(

        @Valid
        List<TeamReplacement> replacements
) {
}

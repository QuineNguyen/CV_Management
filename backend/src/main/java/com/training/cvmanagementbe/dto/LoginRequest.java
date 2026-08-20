package com.training.cvmanagementbe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credentials payload for standard username/password login")
public record LoginRequest(
        @NotBlank
        @Schema(example = "admin")
        String username,

        @NotBlank
        @Schema(example = "AdminP@ss123")
        String password
) {
}

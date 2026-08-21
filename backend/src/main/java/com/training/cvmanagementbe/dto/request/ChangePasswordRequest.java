package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ChangePasswordRequest", description = "Payload for changing current user's password")
public record ChangePasswordRequest(
        @NotBlank
        @Schema(example = "CurrentP@ss123")
        String currentPassword,

        @NotBlank
        @Schema(example = "NewP@ssword456")
        String newPassword,

        @NotBlank
        @Schema(example = "NewP@ssword456")
        String confirmPassword
) {
}

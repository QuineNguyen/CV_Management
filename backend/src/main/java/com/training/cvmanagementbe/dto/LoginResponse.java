package com.training.cvmanagementbe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginResponse", description = "Authentication response containing JWT access token and user profile")
public record LoginResponse(
        String token,
        AuthenticatedUser user
) {
}

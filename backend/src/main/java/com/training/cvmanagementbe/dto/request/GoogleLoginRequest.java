package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// Google ID token obtained by the frontend via Google Identity Services (GIS) or Google Sign-In API. This token is sent to the backend for verification and authentication.
@Schema(name = "GoogleLoginRequest", description = "Payload for authenticating with Google OAuth2 ID token")
public record GoogleLoginRequest(
        @NotBlank String idToken
) {
}

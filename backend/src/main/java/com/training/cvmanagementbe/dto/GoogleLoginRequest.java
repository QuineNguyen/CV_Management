package com.training.cvmanagementbe.dto;

import jakarta.validation.constraints.NotBlank;

// Google ID token obtained by the frontend via Google Identity Services (GIS) or Google Sign-In API. This token is sent to the backend for verification and authentication.
public record GoogleLoginRequest(
        @NotBlank String idToken
) {
}

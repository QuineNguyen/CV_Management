package com.training.cvmanagementbe.dto;

public record LoginResponse(
        String token,
        AuthenticatedUser user
) {
}

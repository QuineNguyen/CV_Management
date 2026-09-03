package com.training.cvmanagementbe.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// Lightweight option for tech lead dropdowns.
@Schema(name = "TechLeadOption", description = "Lightweight option for tech lead dropdowns")
public record TechLeadOption(

        UUID id,
        String fullName,
        String email,
        String username
) {
}

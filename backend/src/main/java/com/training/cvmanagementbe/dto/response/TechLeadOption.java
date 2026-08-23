package com.training.cvmanagementbe.dto.response;

import java.util.UUID;

// Lightweight option for tech lead dropdowns.
public record TechLeadOption(

        UUID id,
        String fullName,
        String email,
        String username
) {
}

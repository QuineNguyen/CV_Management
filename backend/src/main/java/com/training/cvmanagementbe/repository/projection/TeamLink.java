package com.training.cvmanagementbe.repository.projection;

import java.util.UUID;

// A team as a filter option
public record TeamLink(
        UUID id,
        String code,
        String name
) {
}

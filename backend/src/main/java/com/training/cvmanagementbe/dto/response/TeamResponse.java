package com.training.cvmanagementbe.dto.response;

import java.util.UUID;

// Team row returned to the client, tech lead name already resolved.
// A team has no owning department: a project draws members from several.
public record TeamResponse(

        UUID id,
        String code,
        String name,
        String description,
        UUID techLeadId,
        String techLeadFullName,
        int displayOrder,
        long memberCount
) {
}

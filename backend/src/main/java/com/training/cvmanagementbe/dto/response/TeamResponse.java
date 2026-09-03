package com.training.cvmanagementbe.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// Team row returned to the client, tech lead name already resolved.
// A team has no owning department: a project draws members from several.
@Schema(name = "TeamResponse", description = "Team details returned to the client with resolved tech lead name and member count")
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

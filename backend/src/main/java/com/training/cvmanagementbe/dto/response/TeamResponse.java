package com.training.cvmanagementbe.dto.response;

import java.util.UUID;

// Team row returned to the client, department and tech lead names already resolved.
public record TeamResponse(

        UUID id,
        String code,
        String name,
        String description,
        UUID departmentId,
        String departmentCode,
        String departmentName,
        UUID techLeadId,
        String techLeadFullName,
        int displayOrder,
        long memberCount
) {
}

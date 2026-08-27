package com.training.cvmanagementbe.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One profile as the list and detail screens read it.
 *
 * - linkedTeamName and cvCount are resolved server side so the client never has to
 * join two endpoints to render a row.
 */
public record CvProfileResponse(

        UUID id,
        UUID employeeId,
        String name,
        String description,
        boolean primary,
        UUID linkedTeamId,
        String linkedTeamCode,
        String linkedTeamName,
        int cvCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

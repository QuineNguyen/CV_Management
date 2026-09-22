package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/*
 * Both fields are optional in the schema and conditionally required in practice:
 * newName when the old name is taken again, newTeamId when the linked team is no longer one the
 * employee belongs to.
 */
@Schema(name = "CvProfileRestoreRequest", description = "Both fields are optional in the schema and conditionally required in practice: newName when the old name is taken again, newTeamId when the linked team is no longer one the employee belongs to")
public record CvProfileRestoreRequest(
        String newName,
        UUID newTeamId
) {
}

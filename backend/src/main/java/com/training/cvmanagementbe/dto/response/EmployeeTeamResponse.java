package com.training.cvmanagementbe.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// A team the employee belongs to; the only teams a profile may link to
@Schema(name = "EmployeeTeamResponse", description = "A team the employee belongs to; the only teams a profile may link to")
public record EmployeeTeamResponse(

        UUID id,
        String code,
        String name,
        boolean primaryTeam
) {
}

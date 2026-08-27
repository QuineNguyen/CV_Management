package com.training.cvmanagementbe.dto.response;

import java.util.UUID;

// A team the employee belongs to; the only teams a profile may link to
public record EmployeeTeamResponse(

        UUID id,
        String code,
        String name,
        boolean primaryTeam
) {
}

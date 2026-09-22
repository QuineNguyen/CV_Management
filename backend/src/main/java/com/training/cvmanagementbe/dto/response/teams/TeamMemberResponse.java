package com.training.cvmanagementbe.dto.response.teams;

import com.training.cvmanagementbe.enums.users.AccountStatus;
import com.training.cvmanagementbe.enums.users.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// One membership row of a team
@Schema(name = "TeamMemberResponse", description = "One membership row of a team")
public record TeamMemberResponse(

        // team_members.id
        UUID id,
        UUID userId,
        String fullName,
        String email,
        String username,
        Role role,
        AccountStatus status,
        boolean primaryTeam
) {
}

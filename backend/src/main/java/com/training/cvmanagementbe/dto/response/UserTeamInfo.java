package com.training.cvmanagementbe.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// A team the user belongs to, embedded in UserResponse
@Schema(name = "UserTeamInfo", description = "A team the user belongs to, embedded in UserResponse")
public record UserTeamInfo(

        UUID teamId,
        String teamCode,
        String teamName,
        boolean primary
) {
}

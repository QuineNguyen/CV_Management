package com.training.cvmanagementbe.dto.response;

import java.util.UUID;

// A team the user belongs to, embedded in UserResponse
public record UserTeamInfo(

        UUID teamId,
        String teamCode,
        String teamName,
        boolean primary
) {
}

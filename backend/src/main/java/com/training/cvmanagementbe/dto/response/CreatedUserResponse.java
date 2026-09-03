package com.training.cvmanagementbe.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// Returned once after creation so the admin can hand over the temporary password
@Schema(name = "CreatedUserResponse", description = "Returned once after creation so the admin can hand over the temporary password")
public record CreatedUserResponse(

        UserResponse user,
        String temporaryPassword
) {
}

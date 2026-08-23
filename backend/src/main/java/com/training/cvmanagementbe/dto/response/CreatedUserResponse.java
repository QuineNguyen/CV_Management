package com.training.cvmanagementbe.dto.response;

// Returned once after creation so the admin can hand over the temporary password
public record CreatedUserResponse(

        UserResponse user,
        String temporaryPassword
) {
}

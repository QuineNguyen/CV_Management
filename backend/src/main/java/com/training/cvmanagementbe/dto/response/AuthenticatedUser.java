package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

// User payload returned alongside the token. Never exposes the password hash
// Java Record generates getters, constructor, equals(), hashCode() and toString() methods
@Schema(name = "AuthenticatedUser", description = "User payload returned upon successful authentication")
public record AuthenticatedUser(
        @Schema(example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(example = "john.doe")
        String username,

        @Schema(example = "John Doe")
        String fullName,

        @Schema(example = "john.doe@company.com")
        String email,

        @Schema(example = "EMPLOYEE")
        Role role,

        boolean mustChangePassword
) {
    // Static Factory Method: Useful utility to convert Entity User to DTO
    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isMustChangePassword()
        );
    }
}

package com.training.cvmanagementbe.dto;

import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.Role;

import java.util.UUID;

// User payload returned alongside the token. Never exposes the password hash
// Java Record generates getters, constructor, equals(), hashCode() and toString() methods
public record AuthenticatedUser(
        UUID id,
        String username,
        String fullName,
        String email,
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

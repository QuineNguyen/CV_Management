package com.training.cvmanagementbe.config.auth;

import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.Role;

import java.util.UUID;

// Authentication principal held in the SecurityContext for the authenticated user
public record UserPrincipal(UUID userId, String username, Role role) {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getUsername(), user.getRole());
    }
}

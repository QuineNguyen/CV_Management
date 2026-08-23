package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.AccountStatus;
import com.training.cvmanagementbe.enums.Role;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Full user detail returned by the directory endpoints
public record UserResponse(

        UUID id,
        String fullName,
        String email,
        String username,
        Role role,
        AccountStatus status,
        UUID primaryDepartmentId,
        String departmentCode,
        String departmentName,
        LocalDate dateOfBirth,
        String phoneNumber,
        String address,
        UUID avatarImageId,
        List<UserTeamInfo> teams,
        // Teams this user currently leads, drives the deactivate dialog.
        List<TeamResponse> ledTeams,
        boolean mustChangePassword,
        LocalDateTime createdAt
) {
}

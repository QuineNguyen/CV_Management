package com.training.cvmanagementbe.dto.response.profile_updates;

import com.training.cvmanagementbe.enums.Role;

import java.time.LocalDate;
import java.util.UUID;

/*
 * The My Profile screen in one call: the user's own record plus the state of their most recent
 * update request, so the page can show the PENDING/APPROVED/REJECTED banner without a second
 * round trip.
 */
public record MyProfileResponse(
        UUID id,
        String fullName,
        String email,
        String username,
        Role role,
        String departmentCode,
        String departmentName,
        LocalDate dateOfBirth,
        String phoneNumber,
        String address,
        UUID avatarImageId,
        String avatarUrl,
        boolean requiresApproval,
        ProfileUpdateRequestResponse latestUpdateRequest
) {
}

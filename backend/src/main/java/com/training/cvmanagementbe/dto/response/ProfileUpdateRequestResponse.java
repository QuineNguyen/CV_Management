package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.ProfileUpdateStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Carries both sides of the change so the reviewer never has to open a second screen: current_*
 * is read from the user at response time, requested_* is the frozen snapshot.
 *
 * - A requested_* field that is null was not part of the request and stays unchanged on approval.
 */
public record ProfileUpdateRequestResponse(
        UUID id,
        UUID userId,
        String userFullName,
        String userEmail,
        ProfileUpdateStatus status,

        // ---------- Current values, resolved live ----------
        String currentFullName,
        LocalDate currentDateOfBirth,
        String currentPhoneNumber,
        String currentAddress,
        UUID currentAvatarImageId,
        String currentAvatarUrl,

        // ---------- Requested values, frozen at submit time ----------
        String requestedFullName,
        LocalDate requestedDateOfBirth,
        String requestedPhoneNumber,
        String requestedAddress,
        UUID requestedAvatarImageId,
        String requestedAvatarUrl,

        // ---------- Review outcome ----------
        String reviewedByName,
        LocalDateTime reviewedAt,
        String rejectReason,

        LocalDateTime createdAt
) {
}

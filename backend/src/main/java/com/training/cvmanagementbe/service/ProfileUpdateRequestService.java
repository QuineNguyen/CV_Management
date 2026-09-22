package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.profile_updates.ProfileUpdateSubmitRequest;
import com.training.cvmanagementbe.dto.request.profile_updates.RejectProfileUpdateRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.profile_updates.ProfileUpdateRequestResponse;
import com.training.cvmanagementbe.enums.ProfileUpdateStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProfileUpdateRequestService {

    // ---------- Requester side ----------

    /*
     * Opens a request for the caller. Never reached for an Admin - they write directly
     * and MyProfileService is where that branch lives.
     *
     * A second open request is refused by the pending_slot unique index, not by a lookup here.
     */
    ProfileUpdateRequestResponse submit(UUID userId, ProfileUpdateSubmitRequest request);

    // The most recent request whatever its outcome or null when the user never submitted one.
    ProfileUpdateRequestResponse getLatest(UUID userId);

    // Withdraws the caller's open request: A transition to CANCELLED, never a row delete.
    void withdrawPending(UUID userId);

    // ---------- Reviewer side ----------

    PagedResponse<ProfileUpdateRequestResponse> list(ProfileUpdateStatus status, Pageable pageable);

    ProfileUpdateRequestResponse getById(UUID id);

    /*
     * Writes every proposed value that is not empty onto the user and closes the request.
     * An empty field was not part of the proposal, so the current value stays.
     */
    ProfileUpdateRequestResponse approve(UUID id);

    // Closes the request with a mandatory reason; the user record is untouched.
    ProfileUpdateRequestResponse reject(UUID id, RejectProfileUpdateRequest request);

    // Scoped like list(): a badge counting rows the viewer cannot open is worse than no badge
    long countPending();
}

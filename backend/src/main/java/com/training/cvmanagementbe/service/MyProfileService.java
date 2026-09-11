package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.ProfileUpdateSubmitRequest;
import com.training.cvmanagementbe.dto.response.MyProfileResponse;

import java.util.UUID;

public interface MyProfileService {

    /*
     * The caller's own record plus their latest update request.
     * Kept out of UserService: composing the two would make UserServiceImpl and
     * ProfileUpdateRequestServiceImpl depend on each other.
     */
    MyProfileResponse getMyProfile(UUID userId);

    /*
     * Applies the caller's proposed changes and returns the resulting profile.
     *
     * The branch is server-side and invisible to the client: an Admin's values are
     * written straight onto their record with an audit entry, everyone else gets a PENDING
     * request. The caller does not decide which - their role does.
     */
    MyProfileResponse saveMyProfile(UUID userId, ProfileUpdateSubmitRequest request);
}

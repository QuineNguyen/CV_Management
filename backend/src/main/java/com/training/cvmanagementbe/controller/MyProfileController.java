package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.dto.request.profile_updates.ProfileUpdateSubmitRequest;
import com.training.cvmanagementbe.dto.response.profile_updates.MyProfileResponse;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.service.MyProfileService;
import com.training.cvmanagementbe.service.ProfileUpdateRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPath.ME)
@RequiredArgsConstructor
@Tag(name = "My Profile", description = "Self-service profile view and update requests")
public class MyProfileController {

    private final MyProfileService myProfileService;
    private final ProfileUpdateRequestService profileUpdateRequestService;

    @GetMapping(ApiPath.MY_PROFILE)
    @Operation(summary = "Get the signed-in user's profile and latest update request")
    public ResponseEntity<MyProfileResponse> getMyProfile() {
        return ResponseEntity.ok(myProfileService.getMyProfile(CurrentActor.requireUserId()));
    }

    @PutMapping(ApiPath.MY_PROFILE)
    @Operation(summary = "Propose changes to my own profile; an Admin's are applied immediately")
    public ResponseEntity<MyProfileResponse> save(@Valid @RequestBody ProfileUpdateSubmitRequest request) {
        return ResponseEntity.ok(myProfileService.saveMyProfile(CurrentActor.requireUserId(), request));
    }

    @DeleteMapping(ApiPath.MY_PROFILE_UPDATE_REQUEST)
    @Operation(summary = "Withdraw my pending request so a new one can be submitted")
    public ResponseEntity<MyProfileResponse> withdraw() {
        UUID userId = CurrentActor.requireUserId();
        profileUpdateRequestService.withdrawPending(userId);
        return ResponseEntity.ok(myProfileService.getMyProfile(userId));
    }
}

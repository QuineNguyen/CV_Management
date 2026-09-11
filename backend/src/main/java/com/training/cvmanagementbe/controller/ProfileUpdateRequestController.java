package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.RejectProfileUpdateRequest;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.dto.response.PendingCountResponse;
import com.training.cvmanagementbe.dto.response.ProfileUpdateRequestResponse;
import com.training.cvmanagementbe.enums.ProfileUpdateSortField;
import com.training.cvmanagementbe.enums.ProfileUpdateStatus;
import com.training.cvmanagementbe.service.ProfileUpdateRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/*
 * Reviewer side of the self-service profile update workflow/
 *
 * HR is in scope alongside Admin because a request only ever carries descriptive data - name,
 * date of birth, phone, address, avatar. It cannot change a role, a department, a team or an
 * account status, so approving one never grants access. Activate, deactivate and password reset
 * stay Admin-only for exactly that reason.
 */
@RestController
@RequestMapping(ApiPath.PROFILE_UPDATE_REQUESTS)
@PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
@RequiredArgsConstructor
@Tag(name = "Profile Update Requests", description = "Admin and HR approval workflow for self-service profile changes")
public class ProfileUpdateRequestController {

    private final ProfileUpdateRequestService profileUpdateRequestService;

    @GetMapping
    @Operation(summary = "List profile update requests, optionally filtered by status")
    public ResponseEntity<PagedResponse<ProfileUpdateRequestResponse>> list(
            @RequestParam(required = false) ProfileUpdateStatus status,
            @RequestParam(defaultValue = "CREATED_AT") ProfileUpdateSortField sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        Sort sort = PageDefaults.sortBy(direction, sortBy.getProperty(),
                ProfileUpdateSortField.ID.getProperty());
        Pageable pageable = PageRequest.of(
                PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort
        );

        // status stays optional here; the queue screen sends PENDING as its own default.
        return ResponseEntity.ok(profileUpdateRequestService.list(status, pageable));
    }

    /*
     * Declared before BY_ID on purpose: Spring matches in declaration order and with /{id} first
     * it would try to read "pending-count" as a UUID and fail on the conversation.
     */
    @GetMapping(ApiPath.PENDING_COUNT)
    @Operation(summary = "Count requests awaiting review that this caller is allowed to open")
    public ResponseEntity<PendingCountResponse> countPending() {
        return ResponseEntity.ok(
                new PendingCountResponse(profileUpdateRequestService.countPending())
        );
    }

    @GetMapping(ApiPath.BY_ID)
    @Operation(summary = "Get one request with both the current and the proposed values")
    public ResponseEntity<ProfileUpdateRequestResponse> getById(@PathVariable UUID id) {
        // Answers 404 for a request this caller may not review - the direct link must not reveal
        // that a row the list hides exists at all.
        return ResponseEntity.ok(profileUpdateRequestService.getById(id));
    }

    @PostMapping(ApiPath.APPROVE)
    @Operation(summary = "Approve a request and write its proposed values onto the user")
    public ResponseEntity<ProfileUpdateRequestResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(profileUpdateRequestService.approve(id));
    }

    @PostMapping(ApiPath.REJECT)
    @Operation(summary = "Reject a request with a reason the requester will see")
    public ResponseEntity<ProfileUpdateRequestResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectProfileUpdateRequest request) {
        return ResponseEntity.ok(profileUpdateRequestService.reject(id, request));
    }
}

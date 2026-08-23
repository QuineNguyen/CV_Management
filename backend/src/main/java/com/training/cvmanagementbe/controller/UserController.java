package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.CreateUserRequest;
import com.training.cvmanagementbe.dto.request.DeactivateUserRequest;
import com.training.cvmanagementbe.dto.request.UpdateUserRequest;
import com.training.cvmanagementbe.dto.response.CreatedUserResponse;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.dto.response.TechLeadOption;
import com.training.cvmanagementbe.dto.response.UserResponse;
import com.training.cvmanagementbe.enums.AccountStatus;
import com.training.cvmanagementbe.enums.Role;
import com.training.cvmanagementbe.enums.UserSortField;
import com.training.cvmanagementbe.service.UserService;
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

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPath.USERS)
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management and directory")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize(AuthorityExpression.DIRECTORY_READER)
    @Operation(summary = "List users, narrowed by the caller data scope")
    public ResponseEntity<PagedResponse<UserResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "FULL_NAME") UserSortField sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        Sort sort = Sort.by(direction, sortBy.getProperty())
                .and(Sort.by(Sort.Order.asc(UserSortField.FULL_NAME.getProperty())));
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);
        return ResponseEntity.ok(userService.search(keyword, role, status, departmentId, pageable));
    }

    @GetMapping(ApiPath.TECH_LEADS)
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "List active tech leads for dropdowns")
    public ResponseEntity<List<TechLeadOption>> getActiveTechLeads() {
        return ResponseEntity.ok(userService.getActiveTechLeads());
    }

    @GetMapping(ApiPath.BY_ID)
    @PreAuthorize(AuthorityExpression.DIRECTORY_READER)
    @Operation(summary = "Get a user by id, subject to the caller data scope")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize(AuthorityExpression.ADMIN)
    @Operation(summary = "Create a user and return the temporary password once")
    public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        CreatedUserResponse created = userService.create(request);
        return ResponseEntity
                .created(URI.create(ApiPath.USERS + "/" + created.user().id()))
                .body(created);
    }

    @PutMapping(ApiPath.BY_ID)
    @PreAuthorize(AuthorityExpression.ADMIN)
    @Operation(summary = "Update a user. Email and username stay immutable")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PostMapping(ApiPath.DEACTIVATE)
    @PreAuthorize(AuthorityExpression.ADMIN)
    @Operation(summary = "Deactivate a user, hand over led teams and revoke tokens")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false)DeactivateUserRequest request) {
        userService.deactivate(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(ApiPath.ACTIVATE)
    @PreAuthorize(AuthorityExpression.ADMIN)
    @Operation(summary = "Reactive a user")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        userService.activate(id);
        return ResponseEntity.noContent().build();
    }
}

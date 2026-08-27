package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.TeamRequest;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.dto.response.TeamMemberResponse;
import com.training.cvmanagementbe.dto.response.TeamResponse;
import com.training.cvmanagementbe.enums.TeamSortField;
import com.training.cvmanagementbe.service.TeamService;
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
@RequestMapping(ApiPath.TEAMS)
@PreAuthorize(AuthorityExpression.ADMIN)
@RequiredArgsConstructor
@Tag(name = "Teams", description = "Team management (Admin only)")
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @Operation(summary = "List teams with pagination and keyword search")
    public ResponseEntity<PagedResponse<TeamResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "DISPLAY_ORDER") TeamSortField sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        Sort sort = PageDefaults.sortBy(direction, sortBy.getProperty(),
                TeamSortField.DISPLAY_ORDER.getProperty());
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);
        return ResponseEntity.ok(teamService.search(keyword, pageable));
    }

    @GetMapping(ApiPath.BY_ID)
    @Operation(summary = "Get a team by id")
    public ResponseEntity<TeamResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(teamService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a team")
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody TeamRequest request) {
        TeamResponse created = teamService.create(request);
        return ResponseEntity
                .created(URI.create(ApiPath.TEAMS + "/" + created.id()))
                .body(created);
    }

    @PutMapping(ApiPath.BY_ID)
    @Operation(summary = "Update a team")
    public ResponseEntity<TeamResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody TeamRequest request) {
        return ResponseEntity.ok(teamService.update(id, request));
    }

    @DeleteMapping(ApiPath.BY_ID)
    @Operation(summary = "Delete an empty team (no member, no linked CV profile")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        teamService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(ApiPath.MEMBERS)
    @Operation(summary = "List members of a team")
    public ResponseEntity<List<TeamMemberResponse>> getMembers(@PathVariable UUID id) {
        return ResponseEntity.ok(teamService.getMembers(id));
    }

    @PostMapping(ApiPath.MEMBER_BY_USER)
    @Operation(summary = "Add an active user to the team")
    public ResponseEntity<Void> addMember(@PathVariable UUID id, @PathVariable UUID userId) {
        teamService.addMember(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(ApiPath.MEMBER_BY_USER)
    @Operation(summary = "Remove a member from the team")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        teamService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}

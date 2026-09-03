package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.CvProfileRequest;
import com.training.cvmanagementbe.dto.response.ApiResponse;
import com.training.cvmanagementbe.dto.response.CvProfileResponse;
import com.training.cvmanagementbe.dto.response.EmployeeTeamResponse;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.enums.CvProfileSortField;
import com.training.cvmanagementbe.service.CvProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/*
 * Two resource families in one controller because they are one feature: profiles hang off an
 * employee for listing and creation and are addressed directly once they exist. No class-level
 * mapping, so each method spells out its full path.
 *
 * - Scope is enforced in the service, not here: whether a caller may read or manage a profile
 * depends on who owns it, which the path alone does not say.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "CV Profiles", description = "Competency profile management")
public class CvProfileController {

    private final CvProfileService cvProfileService;

    @GetMapping(ApiPath.EMPLOYEES + ApiPath.PROFILES_BY_EMPLOYEE)
    @Operation(summary = "List the active profiles of one employee, primary first")
    public ResponseEntity<PagedResponse<CvProfileResponse>> listByEmployee(
            @PathVariable UUID employeeId,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "NAME") CvProfileSortField sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        // The primary profile always leads the list; the requested sort orders the rest.
        Sort sort = Sort.by(Sort.Order.desc("primary"))
                .and(Sort.by(direction, sortBy.getProperty()));
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);

        return ResponseEntity.ok(cvProfileService.listByEmployee(employeeId, pageable));
    }

    @GetMapping(ApiPath.EMPLOYEES + ApiPath.PROFILE_TEAM_OPTIONS)
    @Operation(summary = "Teams the employee may link a profile to")
    public ResponseEntity<List<EmployeeTeamResponse>> listAssignableTeams(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(cvProfileService.listAssignableTeams(employeeId));
    }

    @PostMapping(ApiPath.EMPLOYEES + ApiPath.PROFILES_BY_EMPLOYEE)
    @Operation(summary = "Create a profile for one employee")
    public ResponseEntity<CvProfileResponse> create(@PathVariable UUID employeeId,
                                                    @Valid @RequestBody CvProfileRequest request) {
        CvProfileResponse created = cvProfileService.create(employeeId, request);
        return ResponseEntity
                .created(URI.create(ApiPath.PROFILES + "/" + created.id()))
                .body(created);
    }

    @PostMapping(ApiPath.EMPLOYEES + ApiPath.PROFILE_ENSURE)
    @Operation(summary = "Return the primary profile, creating the first one if the employee has none")
    public ResponseEntity<CvProfileResponse> ensure(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(cvProfileService.ensureProfileExists(employeeId));
    }

    @GetMapping(ApiPath.PROFILES + ApiPath.BY_ID)
    @Operation(summary = "Get one profile")
    public ResponseEntity<CvProfileResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(cvProfileService.getById(id));
    }

    @PutMapping(ApiPath.PROFILES + ApiPath.BY_ID)
    @Operation(summary = "Update a profile")
    public ResponseEntity<CvProfileResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody CvProfileRequest request) {
        return ResponseEntity.ok(cvProfileService.update(id, request));
    }

    @DeleteMapping(ApiPath.PROFILES + ApiPath.BY_ID)
    @Operation(summary = "Soft-delete a profile and its CVs")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        cvProfileService.delete(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping(ApiPath.PROFILES + ApiPath.SET_PRIMARY)
    @Operation(summary = "Make this the employee's primary profile")
    public ResponseEntity<CvProfileResponse> setPrimary(@PathVariable UUID id) {
        return ResponseEntity.ok(cvProfileService.setPrimary(id));
    }
}

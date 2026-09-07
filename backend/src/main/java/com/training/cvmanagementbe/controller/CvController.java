package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.CvCreateRequest;
import com.training.cvmanagementbe.dto.request.CvDeleteRequest;
import com.training.cvmanagementbe.dto.request.CvEditRequest;
import com.training.cvmanagementbe.dto.response.*;
import com.training.cvmanagementbe.enums.CvSortField;
import com.training.cvmanagementbe.service.CvService;
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
 * Cvs are addressed two ways: as a collection under their profile (create, list) and directly once
 * they exist. No class-level mapping, so each method states its full path.
 *
 * Scope lives in the service, not here: whether a caller may edit depends on who owns the CV,
 * which the path does not say.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "CVs", description = "CV lifecycle and published versions")
public class CvController {

    private final CvService cvService;

    @PostMapping(ApiPath.PROFILES + ApiPath.CVS_BY_PROFILE)
    @Operation(summary = "Create a CV in one profile and language")
    public ResponseEntity<CvResponse> create(@PathVariable UUID profileId,
                                             @Valid @RequestBody CvCreateRequest request) {
        CvResponse created = cvService.create(profileId, request);
        return ResponseEntity
                .created(URI.create(ApiPath.CVS + "/" + created.id()))
                .body(created);
    }

    @GetMapping(ApiPath.PROFILES + ApiPath.CVS_BY_PROFILE)
    @Operation(summary = "List the CVs of one profile")
    public ResponseEntity<List<CvResponse>> listByProfile(
            @PathVariable UUID profileId,
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        return ResponseEntity.ok(cvService.listByProfile(profileId, includeDeleted));
    }

    @GetMapping(ApiPath.CVS + ApiPath.DELETED)
    @Operation(summary = "List soft-deleted CVs (Admin/HR)")
    public ResponseEntity<PagedResponse<CvResponse>> listDeleted(
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "DELETED_AT") CvSortField sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(
                PageDefaults.clampPage(page),
                PageDefaults.clampSize(size),
                Sort.by(direction, sortBy.getProperty())
        );

        return ResponseEntity.ok(cvService.listDeleted(pageable));
    }

    @GetMapping(ApiPath.CVS + ApiPath.BY_ID)
    @Operation(summary = "Get a CV with its current version")
    public ResponseEntity<CvDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(cvService.getById(id));
    }

    @GetMapping(ApiPath.CVS + ApiPath.VERSIONS)
    @Operation(summary = "List the published versions of a CV, newest first")
    public ResponseEntity<List<CvVersionSummary>> listVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(cvService.listVersions(id));
    }

    @PutMapping(ApiPath.CVS + ApiPath.CV_CONTENT)
    @Operation(summary = "Write CV content; the owner's role decides draft vs immediate publish")
    public ResponseEntity<CvEditResponse> edit(@PathVariable UUID id,
                                               @Valid @RequestBody CvEditRequest request) {
        return ResponseEntity.ok(cvService.edit(id, request));
    }

    @DeleteMapping(ApiPath.CVS + ApiPath.BY_ID)
    @Operation(summary = "Soft-delete a CV (Admin/HR)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id,
                                                    @RequestBody(required = false) CvDeleteRequest request) {
        cvService.delete(id, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping(ApiPath.CVS + ApiPath.RESTORE)
    @Operation(summary = "Restore a soft-deleted CV (Admin/HR)")
    public ResponseEntity<CvResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(cvService.restore(id));
    }
}

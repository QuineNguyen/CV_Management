package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.common.PdfResponses;
import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.cvs.*;
import com.training.cvmanagementbe.dto.response.configs.ApiResponse;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.*;
import com.training.cvmanagementbe.dto.response.cvs.pdf.CvPdfLinkResponse;
import com.training.cvmanagementbe.enums.cvs.CvSearchSortField;
import com.training.cvmanagementbe.enums.cvs.CvSortField;
import com.training.cvmanagementbe.enums.cvs.CvVersionSortField;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.service.CvSearchService;
import com.training.cvmanagementbe.service.CvService;
import com.training.cvmanagementbe.service.PdfExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
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
 *
 * One endpoint here is public: openPdfLink. Which paths skip the JWT is decided by PublicEndpoint,
 * not by this class.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "CVs", description = "CV lifecycle and published versions")
public class CvController {

    private final CvService cvService;
    private final CvSearchService cvSearchService;
    private final PdfExportService pdfExportService;

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

    @GetMapping(ApiPath.PROFILES + ApiPath.CVS_SCAFFOLD)
    @Operation(summary = "Starting content for a new CV: master structure as untranslated placeholders or the template for the first CV")
    public ResponseEntity<CvContent> getScaffold(@PathVariable UUID profileId) {
        return ResponseEntity.ok(cvService.getScaffold(profileId));
    }

    // Scope by role, organization + content criteria, paged on the server
    @GetMapping(ApiPath.CVS)
    @Operation(summary = "Search CVs; skills and full text read the latest published version only")
    public ResponseEntity<PagedResponse<CvSearchItemResponse>> search(
            @ParameterObject @Valid CvSearchFilterRequest filter,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "EMPLOYEE_NAME") CvSearchSortField sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(
                PageDefaults.clampPage(page),
                PageDefaults.clampSize(size),
                Sort.by(direction, sortBy.getProperty())
        );

        return ResponseEntity.ok(cvSearchService.search(filter, pageable));
    }

    @GetMapping(ApiPath.CVS + ApiPath.SEARCH_FILTERS)
    @Operation(summary = "Department tree and teams for the CV search filters")
    public ResponseEntity<CvSearchFiltersResponse> getSearchFilters() {
        return ResponseEntity.ok(cvSearchService.getFilterOptions());
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
    @Operation(summary = "List the published versions of a CV, newest first by default")
    public ResponseEntity<PagedResponse<CvVersionHistoryItem>> listVersions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "VERSION_NUMBER") CvVersionSortField sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(
                PageDefaults.clampPage(page),
                PageDefaults.clampSize(size),
                Sort.by(direction, sortBy.getProperty())
        );

        return ResponseEntity.ok(cvService.listVersions(id, pageable));
    }

    @GetMapping(ApiPath.CVS + ApiPath.VERSION_DIFF)
    @Operation(summary = "Compare two published versions; without 'from', 'to' is compared with an empty CV")
    public ResponseEntity<VersionDiffResponse> diffVersions(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID from,
            @RequestParam UUID to
    ) {
        return ResponseEntity.ok(cvService.diffVersions(id, from, to));
    }

    // ---------- PDF export ----------

    /*
     * Direct download for API clients and Swagger; the web app opens a signed link instead.
     * No 'produces' on the mapping: It would also bind the error responses to application/pdf
     * and the JSON error body of a 403/404 could no longer be written.
     */
    @GetMapping(ApiPath.CVS + ApiPath.VERSION_EXPORT_PDF)
    @Operation(summary = "Export a published version as PDF (standard 9-section template, no watermark); preview=true renders inline")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable UUID id,
            @PathVariable UUID versionId,
            @RequestParam(defaultValue = "false") boolean preview
    ) {
        return PdfResponses.of(pdfExportService.export(id, versionId), preview);
    }

    // POST: Issuing a link is the audited export, not a plain read
    @PostMapping(ApiPath.CVS + ApiPath.VERSION_PDF_LINK)
    @Operation(summary = "Issue a short-lived signed link that opens a published version as PDF in the browser")
    public ResponseEntity<CvPdfLinkResponse> createPdfLink(@PathVariable UUID id, @PathVariable UUID versionId) {
        return ResponseEntity.ok(pdfExportService.createLink(id, versionId));
    }

    /*
     * Public (PublicEndpoint.PDF_LINK): an iframe or a new browser tab cannot send the JWT header,
     * so the signed token in the path is the credential. The trailing file name segment is
     * cosmetic - it is what a PDF viewer saves under.
     * - @SecurityRequirements() with no value drops the lock in Swagger, so a test there sends no
     * JWT - exactly like the browser does.
     * - fileName is declared only so Swagger shows a field for it; the token alone picks the PDF.
     */
    @GetMapping(ApiPath.PDF_LINKS + ApiPath.PDF_LINK_FILE)
    @SecurityRequirements
    @Operation(summary = "Open an exported CV through a signed link; download=true saves instead of showing it")
    public ResponseEntity<byte[]> openPdfLink(
            @Parameter(description = "Token from the 'path' returned by POST .../pdf-link - not the login JWT")
            @PathVariable String token,
            @Parameter(description = "File name from the same 'path', e.g. CV_Nguyen_Van_A_vi_v2.pdf")
            @PathVariable String fileName,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        return PdfResponses.of(pdfExportService.openLink(token), !download);
    }

    // ---------- Lifecycle ----------

    @PostMapping(ApiPath.CVS + ApiPath.CV_ROLLBACK)
    @Operation(summary = "Roll a CV back to an older version, published as a new version (Admin/HR)")
    public ResponseEntity<CvVersionHistoryItem> rollback(@PathVariable UUID id, @Valid @RequestBody CvRollbackRequest request) {
        return ResponseEntity.ok(cvService.rollback(id, request));
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

    @GetMapping(ApiPath.CVS + ApiPath.UPDATE_REQUESTS_PENDING_NOTES)
    @Operation(summary = "Feedback notes of the pending update request on a CV, flat per anchor")
    public ResponseEntity<List<PendingNoteResponse>> getPendingNotes(@PathVariable UUID id) {
        return ResponseEntity.ok(cvService.getPendingNotes(id));
    }
}

package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.cvs.BatchPreviewRequest;
import com.training.cvmanagementbe.dto.request.cvs.CreateBatchRequest;
import com.training.cvmanagementbe.dto.request.cvs.CreateSingleUpdateRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.BatchPreviewResponse;
import com.training.cvmanagementbe.dto.response.cvs.BatchRequestResponse;
import com.training.cvmanagementbe.dto.response.cvs.CreateUpdateRequestResponse;
import com.training.cvmanagementbe.dto.response.cvs.UpdateRequestResponse;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.cvs.UpdateRequestSortField;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.record.cvs.UpdateRequestCriteria;
import com.training.cvmanagementbe.service.BatchRequestService;
import com.training.cvmanagementbe.service.UpdateRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(ApiPath.UPDATE_REQUESTS)
@RequiredArgsConstructor
@Tag(name = "Update Requests", description = "CV update requests from Admin/HR to employees")
public class UpdateRequestController {

    private final UpdateRequestService updateRequestService;
    private final BatchRequestService batchRequestService;

    @PostMapping
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "Request one employee to update a CV; ALL creates one request per language")
    public ResponseEntity<CreateUpdateRequestResponse> create(@Valid @RequestBody CreateSingleUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(updateRequestService.create(request));
    }

    @GetMapping
    @Operation(summary = "List update requests, narrowed by the caller scope")
    public ResponseEntity<PagedResponse<UpdateRequestResponse>> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Language language,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID batchId,
            @RequestParam(defaultValue = "CREATED_AT") UpdateRequestSortField sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        Sort sort = PageDefaults.sortBy(direction, sortBy.getProperty(),
                UpdateRequestSortField.CREATED_AT.getProperty());
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);
        UpdateRequestCriteria criteria = new UpdateRequestCriteria(status, departmentId, language, fromDate, toDate, batchId);
        return ResponseEntity.ok(updateRequestService.search(criteria, pageable));
    }

    // RBAC here; "HR only their own" is enforced in the service
    @PostMapping(ApiPath.UPDATE_REQUESTS_CANCEL)
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "Cancel a pending update request (Admin any, HR only their own)")
    public ResponseEntity<UpdateRequestResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(updateRequestService.cancel(id));
    }

    @PostMapping(ApiPath.BATCH_PREVIEW)
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "Preview a batch: who gets a request and who is skipped, each list paged")
    public ResponseEntity<BatchPreviewResponse> previewBatch(
            @Valid @RequestBody BatchPreviewRequest request,
            @RequestParam(defaultValue = PageDefaults.PAGE) int includedPage,
            @RequestParam(defaultValue = PageDefaults.PAGE) int excludedPage,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        int pageSize = PageDefaults.clampSize(size);
        return ResponseEntity.ok(batchRequestService.preview(
                request,
                PageRequest.of(PageDefaults.clampPage(includedPage), pageSize),
                PageRequest.of(PageDefaults.clampPage(excludedPage), pageSize)
        ));
    }

    @PostMapping(ApiPath.BATCH_CREATE)
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "Create a batch; notifications are sent in the background")
    public ResponseEntity<BatchRequestResponse> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        BatchRequestResponse created = batchRequestService.create(request);
        return ResponseEntity
                .created(URI.create(ApiPath.BATCH_REQUESTS + "/" + created.id()))
                .body(created);
    }
}

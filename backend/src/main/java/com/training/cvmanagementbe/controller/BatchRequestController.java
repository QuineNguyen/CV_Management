package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.BatchCancelResponse;
import com.training.cvmanagementbe.dto.response.cvs.BatchFailedItemResponse;
import com.training.cvmanagementbe.dto.response.cvs.BatchRequestResponse;
import com.training.cvmanagementbe.enums.cvs.BatchRequestSortField;
import com.training.cvmanagementbe.enums.cvs.BatchRequestStatus;
import com.training.cvmanagementbe.service.BatchRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiPath.BATCH_REQUESTS)
@RequiredArgsConstructor
@Tag(name = "Batch requests", description = "Progress and failed emails of batch update requests")
public class BatchRequestController {

    private final BatchRequestService batchRequestService;

    @GetMapping(ApiPath.BATCH_DETAIL)
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "Get a batch with its counters; polled while PROCESSING")
    public ResponseEntity<BatchRequestResponse> getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(batchRequestService.getDetail(id));
    }

    @GetMapping(ApiPath.BATCH_FAILED_ITEMS)
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "List the children whose email failed after all retries")
    public ResponseEntity<PagedResponse<BatchFailedItemResponse>> getFailedItems(
            @PathVariable UUID id,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        // Order is fixed by the query: employee name, then request id
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size));
        return ResponseEntity.ok(batchRequestService.getFailedItems(id, pageable));
    }

    @PostMapping(ApiPath.BATCH_RESEND_FAILED)
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "Queue the failed emails again; creates no new request")
    public ResponseEntity<BatchRequestResponse> resendFailed(@PathVariable UUID id) {
        return ResponseEntity.ok(batchRequestService.resendFailed(id));
    }

    @GetMapping
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "List batches, newest first, optionally by status")
    public ResponseEntity<PagedResponse<BatchRequestResponse>> list(
            @RequestParam(required = false)BatchRequestStatus status,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        // Fixed order: the newest batch is the one being watched
        Pageable pageable = PageRequest.of(
                PageDefaults.clampPage(page),
                PageDefaults.clampSize(size),
                Sort.by(Sort.Direction.DESC,
                        BatchRequestSortField.CREATED_AT.getProperty(),
                        BatchRequestSortField.ID.getProperty())
        );
        return ResponseEntity.ok(batchRequestService.list(status, pageable));
    }

    @PostMapping(ApiPath.BATCH_CANCEL)
    @PreAuthorize(AuthorityExpression.ADMIN_OR_HR)
    @Operation(summary = "Cancel a batch request and all its pending update requests")
    public ResponseEntity<BatchCancelResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(batchRequestService.cancel(id));
    }
}

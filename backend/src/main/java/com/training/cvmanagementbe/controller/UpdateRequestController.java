package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.AuthorityExpression;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.cvs.CreateSingleUpdateRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.CreateUpdateRequestResponse;
import com.training.cvmanagementbe.dto.response.cvs.UpdateRequestResponse;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.cvs.UpdateRequestSortField;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.record.cvs.UpdateRequestCriteria;
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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(ApiPath.UPDATE_REQUESTS)
@RequiredArgsConstructor
@Tag(name = "Update Requests", description = "CV update requests from Admin/HR to employees")
public class UpdateRequestController {

    private final UpdateRequestService updateRequestService;

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
            @RequestParam(defaultValue = "CREATED_AT") UpdateRequestSortField sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size
    ) {
        Sort sort = PageDefaults.sortBy(direction, sortBy.getProperty(),
                UpdateRequestSortField.CREATED_AT.getProperty());
        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);
        UpdateRequestCriteria criteria = new UpdateRequestCriteria(status, departmentId, language, fromDate, toDate);
        return ResponseEntity.ok(updateRequestService.search(criteria, pageable));
    }
}

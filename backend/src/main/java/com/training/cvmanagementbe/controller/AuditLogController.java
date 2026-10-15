package com.training.cvmanagementbe.controller;

import com.training.cvmanagementbe.constant.ApiPath;
import com.training.cvmanagementbe.constant.PageDefaults;
import com.training.cvmanagementbe.dto.request.audit.AuditLogFilterRequest;
import com.training.cvmanagementbe.dto.response.audit.AuditLogDetailResponse;
import com.training.cvmanagementbe.dto.response.audit.AuditLogResponse;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.enums.audit.AuditLogField;
import com.training.cvmanagementbe.enums.audit.AuditLogSortField;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Audit logs", description = "Read-only audit trail with dynamic filters (Admin)")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping(ApiPath.AUDIT_LOGS)
    @Operation(summary = "Search the audit trail by actor, action, target type and date range (Admin)")
    public ResponseEntity<PagedResponse<AuditLogResponse>> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) Action action,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
            @RequestParam(defaultValue = PageDefaults.SIZE) int size,
            @RequestParam(defaultValue = "CREATED_AT") AuditLogSortField sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        Sort sort = Sort.by(direction, sortBy.getProperty());
        // Newest first inside a group, then id so paging never repeats or skips a row
        if (sortBy != AuditLogSortField.CREATED_AT) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, AuditLogField.CREATED_AT.getProperty()));
        }
        sort = sort.and(Sort.by(Sort.Direction.DESC, AuditLogField.ID.getProperty()));

        Pageable pageable = PageRequest.of(PageDefaults.clampPage(page), PageDefaults.clampSize(size), sort);
        AuditLogFilterRequest filter = new AuditLogFilterRequest(actorId, action, targetType, fromDate, toDate);
        return ResponseEntity.ok(auditLogService.search(filter, pageable));
    }

    @GetMapping(ApiPath.AUDIT_LOGS + ApiPath.BY_ID)
    @Operation(summary = "One audit entry with its old and new values (Admin)")
    public ResponseEntity<AuditLogDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditLogService.getById(id));
    }

    @GetMapping(ApiPath.AUDIT_LOGS + ApiPath.AUDIT_LOG_ACTIONS)
    @Operation(summary = "Action values for the filter dropdown (Admin)")
    public ResponseEntity<List<Action>> listActions() {
        return ResponseEntity.ok(auditLogService.listActions());
    }

    @GetMapping(ApiPath.AUDIT_LOGS + ApiPath.AUDIT_LOG_TARGET_TYPES)
    @Operation(summary = "Target type values for the filter dropdown (Admin)")
    public ResponseEntity<List<TargetType>> listTargetTypes() {
        return ResponseEntity.ok(auditLogService.listTargetTypes());
    }
}

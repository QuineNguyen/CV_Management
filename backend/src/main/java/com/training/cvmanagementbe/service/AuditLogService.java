package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.audit.AuditLogFilterRequest;
import com.training.cvmanagementbe.dto.response.audit.AuditLogDetailResponse;
import com.training.cvmanagementbe.dto.response.audit.AuditLogResponse;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.TargetType;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AuditLogService {

    PagedResponse<AuditLogResponse> search(AuditLogFilterRequest filter, Pageable pageable);

    AuditLogDetailResponse getById(UUID id);

    // Filter options, alphabetical
    List<Action> listActions();

    List<TargetType> listTargetTypes();
}

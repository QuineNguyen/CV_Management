package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.cvs.BatchPreviewRequest;
import com.training.cvmanagementbe.dto.request.cvs.CreateBatchRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.BatchFailedItemResponse;
import com.training.cvmanagementbe.dto.response.cvs.BatchPreviewResponse;
import com.training.cvmanagementbe.dto.response.cvs.BatchRequestResponse;
import com.training.cvmanagementbe.enums.cvs.BatchRequestStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BatchRequestService {

    BatchPreviewResponse preview(BatchPreviewRequest request, Pageable includedPage, Pageable excludedPage);

    BatchRequestResponse create(CreateBatchRequest request);

    BatchRequestResponse getDetail(UUID batchId);

    PagedResponse<BatchFailedItemResponse> getFailedItems(UUID batchId, Pageable pageable);

    BatchRequestResponse resendFailed(UUID batchId);

    PagedResponse<BatchRequestResponse> list(BatchRequestStatus status, Pageable pageable);
}

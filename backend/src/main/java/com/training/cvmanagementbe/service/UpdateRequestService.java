package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.cvs.CreateSingleUpdateRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.CreateUpdateRequestResponse;
import com.training.cvmanagementbe.dto.response.cvs.UpdateRequestResponse;
import com.training.cvmanagementbe.record.cvs.UpdateRequestCriteria;
import org.springframework.data.domain.Pageable;

public interface UpdateRequestService {

    // Admin/HR only; ALL creates one request per language and skips taken slots
    CreateUpdateRequestResponse create(CreateSingleUpdateRequest request);

    // Every role; rows are narrowed to the caller scope server-side
    PagedResponse<UpdateRequestResponse> search(UpdateRequestCriteria criteria, Pageable pageable);
}

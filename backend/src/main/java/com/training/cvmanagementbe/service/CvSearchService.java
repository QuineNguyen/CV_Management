package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.cvs.CvSearchFilterRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.CvSearchFiltersResponse;
import com.training.cvmanagementbe.dto.response.cvs.CvSearchItemResponse;
import org.springframework.data.domain.Pageable;

/*
 * CV search. Role scope and every filter are applied in the query; the client never
 * trims what comes back.
 */
public interface CvSearchService {

    PagedResponse<CvSearchItemResponse> search(CvSearchFilterRequest filter, Pageable pageable);

    // Department tree and team list for the filter dropdowns
    CvSearchFiltersResponse getFilterOptions();
}

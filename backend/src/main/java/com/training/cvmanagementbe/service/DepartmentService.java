package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.request.departments.DepartmentRequest;
import com.training.cvmanagementbe.dto.response.departments.DepartmentResponse;
import com.training.cvmanagementbe.dto.request.departments.MoveDepartmentRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DepartmentService {

    // Page of root nodes, each with its whole subtree nested under children.
    PagedResponse<DepartmentResponse> getTree(Pageable pageable);

    // Flat paginated lookup. excludeSubtreeOf drops that node and its descendants.
    PagedResponse<DepartmentResponse> search(String keyword, UUID excludeSubtreeOf, Pageable pageable);

    // Single node, children left empty
    DepartmentResponse getById(UUID id);

    DepartmentResponse create(DepartmentRequest request);

    DepartmentResponse update(UUID id, DepartmentRequest request);

    // Hard delete. Rejected when children, employees or teams still reference it.
    void delete(UUID id);

    // Repositions one node relative to ites visible neighbours.
    void move(UUID id, MoveDepartmentRequest request);
}

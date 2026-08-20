package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.DepartmentRequest;
import com.training.cvmanagementbe.dto.DepartmentResponse;
import com.training.cvmanagementbe.dto.ReorderRequest;

import java.util.List;
import java.util.UUID;

public interface DepartmentService {

    // Root nodes with children resolved recursively
    List<DepartmentResponse> getTree();

    // Single node, children left empty
    DepartmentResponse getById(UUID id);

    DepartmentResponse create(DepartmentRequest request);

    DepartmentResponse update(UUID id, DepartmentRequest request);

    // Hard delete. Rejected when children, employees or teams still reference it.
    void delete(UUID id);

    // Renumbers every direct child of parentId. Pass null for the root level
    void reorder(UUID parentId, ReorderRequest request);
}

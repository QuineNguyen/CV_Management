package com.training.cvmanagementbe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "DepartmentResponse", description = "Department node, children is empty for flat (single item) responses")
public record DepartmentResponse(
        UUID id,
        String code,
        String name,
        UUID parentDepartmentId,
        int displayOrder,
        List<DepartmentResponse> children
) {
}

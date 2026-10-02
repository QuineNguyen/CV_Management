package com.training.cvmanagementbe.dto.response.departments;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "DepartmentResponse", description = "Department node, children is empty for flat (single item) responses")
public record DepartmentResponse(
        UUID id,
        String code,
        String name,
        UUID parentDepartmentId,
        int displayOrder,
        LocalDateTime updatedAt,
        List<DepartmentResponse> children
) {
}

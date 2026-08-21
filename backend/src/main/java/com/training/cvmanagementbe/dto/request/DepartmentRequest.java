package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "DepartmentRequest", description = "Create / update payload for a department")
public record DepartmentRequest(
        @NotBlank
        @Size(max = 50)
        @Schema(example = "P.KTCN")
        String code,

        @NotBlank
        @Size(max = 255)
        @Schema(example = "Phong Ky thuat Cong nghe")
        String name,

        UUID parentDepartmentId
) {
}

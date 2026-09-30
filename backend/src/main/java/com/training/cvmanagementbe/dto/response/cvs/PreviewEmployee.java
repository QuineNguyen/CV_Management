package com.training.cvmanagementbe.dto.response.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

// Profile null: The employee has none yet and will be asked to create one.
@Schema(name = "PreviewEmployee", description = "A preview of an employee in a batch request")
public record PreviewEmployee(
        String fullName,
        String departmentName,
        String profileName
) {
}

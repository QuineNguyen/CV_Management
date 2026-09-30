package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.BatchExclusionReason;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ExcludedEmployee", description = "An employee that is excluded from a batch request")
public record ExcludedEmployee(
        String fullName,
        BatchExclusionReason reason
) {
}

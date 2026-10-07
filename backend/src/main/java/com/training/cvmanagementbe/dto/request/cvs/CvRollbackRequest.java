package com.training.cvmanagementbe.dto.request.cvs;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(name = "CvRollbackRequest", description = "Request to rollback a CV version")
public record CvRollbackRequest(
        @NotNull
        UUID targetVersionId
) {
}

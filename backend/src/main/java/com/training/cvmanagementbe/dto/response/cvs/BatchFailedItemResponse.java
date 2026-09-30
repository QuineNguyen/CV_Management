package com.training.cvmanagementbe.dto.response.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "BatchFailedItemResponse", description = "Response for a failed item in a batch request")
public record BatchFailedItemResponse(
        UUID updateRequestId,
        String fullName,
        String email,
        String profileName
) {
}

package com.training.cvmanagementbe.dto.response.cvs;

import io.swagger.v3.oas.annotations.media.Schema;

// The batch after the cancel and how many PENDING children it cancelled
@Schema(name = "BatchCancelResponse", description = "Response for batch cancel")
public record BatchCancelResponse(
        BatchRequestResponse batch,
        int cancelledCount
) {
}

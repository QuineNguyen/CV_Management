package com.training.cvmanagementbe.dto.request.profile_updates;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The reason is mandatory: it is the only thing the user sees explaining the refusal.
@Schema(name = "RejectProfileUpdateRequest", description = "The reason is mandatory: it is the only thing the user sees explaining the refusal.")
public record RejectProfileUpdateRequest(

        @NotBlank
        @Size(max = 500)
        String reason
) {
}

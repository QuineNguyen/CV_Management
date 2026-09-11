package com.training.cvmanagementbe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The reason is mandatory: it is the only thing the user sees explaining the refusal.
public record RejectProfileUpdateRequest(

        @NotBlank
        @Size(max = 500)
        String reason
) {
}

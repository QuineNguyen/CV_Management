package com.training.cvmanagementbe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(name = "ReorderRequest", description = "Full sibling list of one parent, in the desired display order")
public record ReorderRequest(
        @NotEmpty
        List<UUID> orderedIds
) {
}

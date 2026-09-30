package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import io.swagger.v3.oas.annotations.media.Schema;

// Both lists page independently; totalElements of "included" is the batch size
@Schema(name = "BatchPreviewResponse", description = "The response for a batch preview request, containing included and excluded employees")
public record BatchPreviewResponse(
        PagedResponse<PreviewEmployee> included,
        PagedResponse<ExcludedEmployee> excluded
) {
}

package com.training.cvmanagementbe.dto.request.cvs;

import com.training.cvmanagementbe.enums.cvs.BatchTargetType;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.cvs.UpdateRequestLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(name = "CreateBatchRequest", description = "Request to create a batch of CVs")
public record CreateBatchRequest(
        @NotNull
        BatchTargetType targetType,

        @NotEmpty
        @Size(max = BatchPreviewRequest.MAX_TARGETS)
        List<@NotNull UUID> targetIds,

        @NotNull
        Language language,

        @NotNull
        LocalDate deadline,

        @NotBlank
        @Size(max = BatchPreviewRequest.REASON_MAX_LENGTH)
        String reason,

        @NotNull
        @Positive
        Integer expectedCount
) {
    public BatchPreviewRequest toPreview() {
        return new BatchPreviewRequest(targetType, targetIds, language, deadline, reason);
    }
}

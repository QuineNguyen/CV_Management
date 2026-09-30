package com.training.cvmanagementbe.dto.request.cvs;

import com.training.cvmanagementbe.enums.cvs.BatchTargetType;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.cvs.UpdateRequestLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(name = "BatchPreviewRequest", description = "Request to preview a batch of CVs before processing")
public record BatchPreviewRequest(
        @NotNull
        BatchTargetType targetType,

        // One id for DEPARTMENT / TEAM, the picked employees for MANUAL
        @NotEmpty
        @Size(max = BatchPreviewRequest.MAX_TARGETS)
        List<@NotNull UUID> targetIds,

        @NotNull
        Language language,

        // Day only; the server adds 23:59:59
        @NotNull
        LocalDate deadline,

        @NotBlank
        @Size(max = BatchPreviewRequest.REASON_MAX_LENGTH)
        String reason
) {
    public static final int MAX_TARGETS = 500;
    public static final int REASON_MAX_LENGTH = 1000;
}

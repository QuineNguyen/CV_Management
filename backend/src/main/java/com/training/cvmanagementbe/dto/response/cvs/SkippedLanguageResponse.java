package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.Language;
import io.swagger.v3.oas.annotations.media.Schema;

// A language left out of an ALL request because its slot already had a PENDING request
@Schema(name = "SkippedLanguageResponse", description = "A language left out of an ALL request because its slot already had a PENDING request")
public record SkippedLanguageResponse(
        Language language,
        String reason
) {
}

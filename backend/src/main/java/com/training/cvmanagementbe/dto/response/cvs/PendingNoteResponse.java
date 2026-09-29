package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

// One anchored note of a PENDING update request, flat so the editor pins it by anchor
@Schema(name = "PendingNoteResponse", description = "One anchored note of a PENDING update request, flat so the editor pins it by anchor")
public record PendingNoteResponse(
        CvSectionKey sectionKey,
        String itemId,
        String fieldKey,
        String note,
        String createdByName,
        LocalDateTime createdAt
) {
}

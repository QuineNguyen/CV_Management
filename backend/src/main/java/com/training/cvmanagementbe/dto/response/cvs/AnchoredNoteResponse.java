package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import io.swagger.v3.oas.annotations.media.Schema;

// itemId and fieldKey are kept because the client places the note at its anchor
@Schema(name = "AnchoredNoteResponse", description = "One feedback note pinned to (section, entry, field)")
public record AnchoredNoteResponse(
        CvSectionKey sectionKey,
        String itemId,
        String fieldKey,
        String note
) {
}

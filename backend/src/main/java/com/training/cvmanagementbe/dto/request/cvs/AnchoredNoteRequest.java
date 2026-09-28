package com.training.cvmanagementbe.dto.request.cvs;

import com.training.cvmanagementbe.enums.cvs.CvSectionKey;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// One feedback note pinned to (section, entry, field)
@Schema(name = "AnchoredNoteRequest", description = "One feedback note pinned to (section, entry, field)")
public record AnchoredNoteRequest(
        @NotNull
        CvSectionKey sectionKey,

        @Size(max = AnchoredNoteRequest.ANCHORED_MAX_LENGTH)
        String itemId,

        @Size(max = AnchoredNoteRequest.ANCHORED_MAX_LENGTH)
        String fieldKey,

        @NotBlank
        @Size(max = AnchoredNoteRequest.NOTE_MAX_LENGTH)
        String note
) {
    // Same limits as inline_comments.item_id / field_key
    public static final int ANCHORED_MAX_LENGTH = 64;
    public static final int NOTE_MAX_LENGTH = 1000;
}

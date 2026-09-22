package com.training.cvmanagementbe.dto.request;

import com.training.cvmanagementbe.enums.CvSectionKey;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/*
 * One comment the reviewer pins to the draft while rejecting it.
 * - itemId: required for REPEATED sections, must be empty for SINGLE ones.
 * Checked in the service, because the rule depends on the section type.
 * - fieldKey: optional; empty means the comment covers the whole item or section.
 */
@Schema(name = "InlineCommentRequest", description = "A comment anchored at (section, item, field)")
public record InlineCommentRequest(
        @NotNull
        CvSectionKey sectionKey,

        @Size(max = 64)
        String itemId,

        @Size(max = 64)
        String fieldKey,

        @NotBlank @Size(max = 2000)
        String content
) {
}

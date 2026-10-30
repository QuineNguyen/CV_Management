package com.training.cvmanagementbe.dto.request.cvs;

import com.training.cvmanagementbe.record.cvs.CvContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Write CV content. Where it lands depends on the owner's choice:
 * an Employee owner updates a draft, an Admin/HR owner publishes a version directly.
 *
 * - expectedDraftUpdatedAt: updatedAt of the open draft the editor loaded, null when it loaded none.
 * The request carries the whole content, so a save over a draft that changed meanwhile (structure
 * sync adding placeholders) would silently drop those changes; a mismatch is a 409 instead.
 */
@Schema(name = "CvEditRequest", description = "Write CV content. Where it lands depends on the owner's choice: an Employee owner updates a draft, an Admin/HR owner publishes a version directly.")
public record CvEditRequest(
        @NotNull
        @Valid
        CvContent content,

        UUID avatarImageId,

        @Schema(description = "updatedAt of the open draft the client loaded; null when no draft was open", nullable = true)
        LocalDateTime expectedDraftUpdatedAt
) {
}

package com.training.cvmanagementbe.dto.request.approvals;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/*
 * A rejection: the overall reason is required, anchored comments are optional.
 * The list is capped so a single request cannot flood the table.
 */
@Schema(name = "RejectDraftRequest", description = "Overall reason plus optional anchored comments")
public record RejectDraftRequest(
        @NotBlank
        @Size(max = 2000)
        String reason,

        @Valid @Size(max = 50)
        List<InlineCommentRequest> comments
) {

    // Absent and empty mean the same thing to the service.
    public List<InlineCommentRequest> commentsOrEmpty() {
        return comments == null ? List.of() : comments;
    }
}

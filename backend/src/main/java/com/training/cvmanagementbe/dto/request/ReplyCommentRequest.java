package com.training.cvmanagementbe.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ReplyCommentRequest", description = "A reply inside an existing comment thread")
public record ReplyCommentRequest(
        @NotBlank @Size(max = 2000)
        String content
) {
}

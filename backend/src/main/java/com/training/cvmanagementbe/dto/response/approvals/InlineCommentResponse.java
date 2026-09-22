package com.training.cvmanagementbe.dto.response.approvals;

import com.training.cvmanagementbe.enums.CvSectionKey;
import com.training.cvmanagementbe.enums.InlineCommentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One comment as the UI renders it.
 * - id: target of a reply.
 * - parentCommentId: groups replies under their root; null on a root.
 * - itemId: an anchor inside content_json, not a table key - the editor places the comment by it.
 * The author is sent as a name only; no screen needs the author's id.
 */
@Schema(name = "InlineCommentResponse", description = "An anchored comment or a reply")
public record InlineCommentResponse(
        UUID id,
        int reviewRound,
        CvSectionKey sectionKey,
        String itemId,
        String fieldKey,
        String authorName,
        String content,
        InlineCommentStatus status,
        UUID parentCommentId,
        LocalDateTime createdAt
) {
}

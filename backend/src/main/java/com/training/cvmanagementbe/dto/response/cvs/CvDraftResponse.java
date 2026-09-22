package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.dto.response.approvals.InlineCommentResponse;
import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import com.training.cvmanagementbe.record.CvContent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "CvDraftResponse", description = "A draft CV with its content and comments")
public record CvDraftResponse(
        UUID id,
        UUID cvId,
        DraftStatus status,
        int reviewRound,
        CvContent content,
        UUID avatarImageId,
        String avatarUrl,
        String lastRejectionReason,
        boolean submittable,
        long untranslatedItemCount,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        List<InlineCommentResponse> inlineComments
) {
}

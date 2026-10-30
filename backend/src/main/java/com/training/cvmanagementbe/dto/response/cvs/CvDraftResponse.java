package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.dto.response.approvals.InlineCommentResponse;
import com.training.cvmanagementbe.enums.cvs.DraftStatus;
import com.training.cvmanagementbe.record.cvs.CvContent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/*
 * - untranslatedItemCount / deletedInMasterCount: Derived from the content, no columns behind them;
 * the editor and the review screen build their structure-sync notice from these two.
 * - updatedAt: Also the token the editor sends back as expectedDraftUpdatedAt.
 */
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
        long deletedInMasterCount,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        List<InlineCommentResponse> inlineComments
) {
}

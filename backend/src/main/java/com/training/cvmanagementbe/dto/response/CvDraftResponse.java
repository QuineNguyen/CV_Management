package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.enums.DraftStatus;
import com.training.cvmanagementbe.record.CvContent;

import java.time.LocalDateTime;
import java.util.UUID;

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
        LocalDateTime updatedAt
) {
}

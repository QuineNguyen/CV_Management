package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.record.CvContent;

import java.util.UUID;

/*
 * The CV plus its current version's content.
 *
 * - content is null when nothing has been published - the screen then shows
 * "no official version yet" rather than an empty CV.
 */
public record CvDetailResponse(
        CvResponse cv,
        CvVersionSummary currentVersion,
        CvContent content,
        UUID avatarImageId
) {
}

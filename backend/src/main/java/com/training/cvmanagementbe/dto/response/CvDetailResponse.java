package com.training.cvmanagementbe.dto.response;

import com.training.cvmanagementbe.record.CvContent;

import java.util.UUID;

/*
 * The CV plus its current version's content.
 *
 * - content is null when nothing has been published - the screen then shows
 * "no official version yet" rather than an empty CV.
 * - openDraft is what the edit screen loads. Seeding the editor from the published
 * version instead would silently discard work an employee saved and came back to.
 */
public record CvDetailResponse(
        CvResponse cv,
        CvVersionSummary currentVersion,
        CvContent content,
        UUID avatarImageId,
        CvDraftResponse openDraft
) {
}

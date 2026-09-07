package com.training.cvmanagementbe.dto.request;

import com.training.cvmanagementbe.enums.Language;
import com.training.cvmanagementbe.record.CvContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/*
 * Create one CV inside the profile.
 *
 * - language: The CV language; must not be occupied by an ACTIVE CV of the same profile.
 * - content: Optional; the server falls back to the standard skeleton.
 * - avatarImageId: Optional image_files reference; never stored inside the content.
 */
public record CvCreateRequest(
        @NotNull
        Language language,

        @Valid
        CvContent content,

        UUID avatarImageId
) {
}

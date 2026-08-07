package com.training.cvmanagementbe.record;

import com.training.cvmanagementbe.enums.ErrorCode;
import com.training.cvmanagementbe.exception.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Coordinate system used to point at a spot inside CV content: (section, item, field).
 *
 * <p>Three different features anchor into CV content — change log entries, inline review comments,
 * and the notes an HR user attaches to an update request. All three must use the same convention;
 * if they drift apart, the same coordinate means different things depending on who wrote it.
 */
public record ContentAnchor(
        @Schema(example = "EXPERIENCE", description = "One of the nine fixed CV sections")
        SectionKey sectionKey,
        @Schema(example = "itm_7f3a", description = "Stable identifier of a repeated item")
        String itemId,
        @Schema(example = "company") String fieldKey
) {
    /** The nine sections of the standard CV template. */
    public enum SectionKey {
        PERSONAL_INFO(SectionType.SINGLE),
        CAREER_OBJECTIVE(SectionType.SINGLE),
        SKILLS(SectionType.REPEATED),
        EXPERIENCE(SectionType.REPEATED),
        EDUCATION(SectionType.REPEATED),
        CERTIFICATIONS(SectionType.REPEATED),
        PROJECTS(SectionType.REPEATED),
        LANGUAGES(SectionType.REPEATED),
        ADDITIONAL_INFO(SectionType.REPEATED);

        private final SectionType type;
        SectionKey(SectionType type) { this.type = type; }
        public SectionType type() { return type; }
    }

    public enum SectionType { SINGLE, REPEATED }

    /**
     * Validates the empty/non-empty rules. This lives in the service layer rather than in a
     * database CHECK because it depends on the section's type, which is part of the JSON content
     * and not a column.
     *
     * - repeated section -> itemId is required
     * - single section -> itemId must be absent (there are no items to point at)
     * - absent fieldKey means the anchor covers the whole item, or the whole section
     */
    public void validate() {
        boolean hasItem = itemId != null && !itemId.isBlank();
        if (sectionKey.type() == SectionType.REPEATED && !hasItem) {
            throw new ApiException.BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Section " + sectionKey + " is repeated, so itemId is required");
        }
        if (sectionKey.type() == SectionType.SINGLE && hasItem) {
            throw new ApiException.BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Section " + sectionKey + " is single, so itemId must be empty");
        }
    }
}

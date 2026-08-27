package com.training.cvmanagementbe.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/*
 * The 9 fixed sections of the standard CV template.
 *
 * The wire value is the snake_case key stored in content_json. Display order is the template
 * default; a CV may reorder sections and reordering the master propagates to every language.
 */
public enum CvSectionKey {

    PERSONAL_INFO("personal_info", CvSectionType.SINGLE, 1),
    CAREER_OBJECTIVE("career_objective", CvSectionType.SINGLE, 2),
    SKILLS("skills", CvSectionType.REPEATED, 3),
    EXPERIENCE("experience", CvSectionType.REPEATED, 4),
    EDUCATION("education", CvSectionType.REPEATED, 5),
    CERTIFICATIONS("certifications", CvSectionType.REPEATED, 6),
    PROJECTS("projects", CvSectionType.REPEATED, 7),
    LANGUAGES("languages", CvSectionType.REPEATED, 8),
    ADDITIONAL_INFO("additional_info", CvSectionType.REPEATED, 9);

    private final String key;
    private final CvSectionType sectionType;
    private final int defaultOrder;

    CvSectionKey(String key, CvSectionType sectionType, int defaultOrder) {
        this.key = key;
        this.sectionType = sectionType;
        this.defaultOrder = defaultOrder;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public CvSectionType sectionType() {
        return sectionType;
    }

    public int defaultOrder() {
        return defaultOrder;
    }

    public boolean repeated() {
        return sectionType == CvSectionType.REPEATED;
    }
}

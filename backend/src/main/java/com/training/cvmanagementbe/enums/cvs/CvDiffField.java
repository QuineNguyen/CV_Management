package com.training.cvmanagementbe.enums.cvs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

// Field keys the diff treats specially. Values are the JSON names used in content_json.
@Getter
@RequiredArgsConstructor
public enum CvDiffField {

    // Column on cv_versions, compared as a personal_info field.
    AVATAR_IMAGE_ID("avatar_image_id"),
    DISPLAY_ORDER("display_order"),
    SKILL_ID("skill_id"),
    IS_UNTRANSLATED("is_untranslated"),
    DELETED_IN_MASTER("deleted_in_master");

    // Position and catalogue link: A reorder or relink is not a content change for a reader.
    public static final Set<String> IGNORED = Set.of(DISPLAY_ORDER.key, SKILL_ID.key);

    // Flags where false means "nothing to report".
    public static final Set<String> FLAGS = Set.of(IS_UNTRANSLATED.key, DELETED_IN_MASTER.key);

    private final String key;
}

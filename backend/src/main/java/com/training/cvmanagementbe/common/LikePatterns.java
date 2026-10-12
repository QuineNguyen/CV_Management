package com.training.cvmanagementbe.common;

/*
 * Builds LIKE patterns from user text.
 * MariaDB's default LIKE escape is the backslash, so wildcards in the text are matched literally.
 */
public final class LikePatterns {

    private LikePatterns() {

    }

    // "%text%" with % _ and \ escaped; null for blank text
    public static String contains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

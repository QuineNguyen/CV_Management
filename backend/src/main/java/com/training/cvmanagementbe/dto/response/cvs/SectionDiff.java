package com.training.cvmanagementbe.dto.response.cvs;

import java.util.List;

/*
 * - sectionKey: snake_case key, e.g. "personal_info", "skills".
 * - items: SINGLE sections carry at most one item, with itemId = null.
 * - changeCount: Items (or fields, for SINGLE) that are not UNCHANGED.
 */
public record SectionDiff(
        String sectionKey,
        int changeCount,
        List<ItemDiff> items
) {
}

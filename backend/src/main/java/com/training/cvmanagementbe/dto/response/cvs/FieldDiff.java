package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.ChangeType;

import java.util.List;

/*
 * - fieldKey: JSON name, e.g. "full_name", "description", or the virtual "avatar_image_id".
 * - inlineDiff: Only for MODIFIED with a value on both sided; null otherwise.
 */
public record FieldDiff(
        String fieldKey,
        ChangeType changeType,
        String oldValue,
        String newValue,
        List<DiffChunk> inlineDiff
) {
}

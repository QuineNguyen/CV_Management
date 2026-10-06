package com.training.cvmanagementbe.dto.response.cvs;

import com.training.cvmanagementbe.enums.cvs.ChangeType;

import java.util.List;

/*
 * - itemId: Null for SINGLE sections; the client uses it as a stable row key.
 * - fields: Every non-empty field, changed or not, so an added or removed entry can be shown in full.
 */
public record ItemDiff(
        String itemId,
        ChangeType changeType,
        List<FieldDiff> fields
) {
}

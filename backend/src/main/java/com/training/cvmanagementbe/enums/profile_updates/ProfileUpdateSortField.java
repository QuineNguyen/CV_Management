package com.training.cvmanagementbe.enums.profile_updates;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Whitelist of sortable columns; the client never sends a raw property name.
@Getter
@RequiredArgsConstructor
public enum ProfileUpdateSortField {

    CREATED_AT("createdAt"),
    REVIEWED_AT("reviewedAt"),
    STATUS("status"),
    ID("id");

    private final String property;
}

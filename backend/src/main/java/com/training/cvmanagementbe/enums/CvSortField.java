package com.training.cvmanagementbe.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Sortable columns exposed by the deleted-CV list; keeps raw property names out of the API.
@Getter
@RequiredArgsConstructor
public enum CvSortField {

    DELETED_AT("deletedAt"),
    LANGUAGE("language"),
    UPDATED_AT("updatedAt");

    private final String property;
}

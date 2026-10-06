package com.training.cvmanagementbe.enums.cvs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Sortable columns of the version history; the request names the constant, never the property.
@Getter
@RequiredArgsConstructor
public enum CvVersionSortField {
    VERSION_NUMBER("versionNumber"),
    PUBLISHED_AT("publishedAt");

    private final String property;
}

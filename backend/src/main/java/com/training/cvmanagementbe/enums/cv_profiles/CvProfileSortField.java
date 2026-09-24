package com.training.cvmanagementbe.enums.cv_profiles;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Sortable columns of the profile list; maps the API value to the JPA property.
@Getter
@RequiredArgsConstructor
public enum CvProfileSortField {

    NAME("name"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt");

    private final String property;
}

package com.training.cvmanagementbe.enums.cvs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

// Sort options of the CV search (by name, by update date)
@Getter
@RequiredArgsConstructor
public enum CvSearchSortField {
    EMPLOYEE_NAME("employeeName"),
    // Publish date of the current version; never-published CVs always go last
    PUBLISHED_AT("publishedAt");

    private final String property;

    public static Optional<CvSearchSortField> fromProperty(String property) {
        return Arrays.stream(values())
                .filter(field -> field.property.equals(property))
                .findFirst();
    }
}

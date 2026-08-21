package com.training.cvmanagementbe.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Sort whitelist. Binding a raw string to Sort would let callers order by any field.
@Getter
@RequiredArgsConstructor
public enum DepartmentSortField {

    DISPLAY_ORDER("displayOrder"),
    CODE("code"),
    NAME("name"),
    CREATED_AT("createdAt");

    private final String property;
}

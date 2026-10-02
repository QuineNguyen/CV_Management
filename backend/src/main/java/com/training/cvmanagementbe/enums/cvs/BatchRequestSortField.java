package com.training.cvmanagementbe.enums.cvs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BatchRequestSortField {

    CREATED_AT("createdAt"),
    ID("id");

    private final String property;
}

package com.training.cvmanagementbe.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TeamSortField {

    CODE("code"),
    NAME("name"),
    DISPLAY_ORDER("displayOrder"),
    CREATED_AT("createdAt");

    private final String property;
}

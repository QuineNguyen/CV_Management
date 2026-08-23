package com.training.cvmanagementbe.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserSortField {

    FULL_NAME("fullName"),
    EMAIL("email"),
    USERNAME("username"),
    ROLE("role"),
    STATUS("status"),
    CREATED_AT("createdAt");

    private final String property;
}

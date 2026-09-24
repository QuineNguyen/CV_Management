package com.training.cvmanagementbe.enums.notifications;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationSortField {

    CREATED_AT("createdAt"),
    // Tie-breaker: one event creates several rows in the same instant
    ID("id");

    private final String property;
}

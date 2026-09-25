package com.training.cvmanagementbe.enums.approvals;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Sortable columns of the approval queue. Whitelisting them here keeps an arbitrary client string
// out of the ORDER BY and keeps the API name independent of the entity property name.
@Getter
@RequiredArgsConstructor
public enum ApprovalSortField {

    DUE_AT("dueAt"),
    ASSIGNED_AT("assignedAt"),
    CLOSED_AT("closedAt"),
    LEVEL("level"),
    REVIEW_ROUND("reviewRound");

    private final String property;
}

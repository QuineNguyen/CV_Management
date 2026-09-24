package com.training.cvmanagementbe.enums.approvals;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// Sortable columns of the admin oversight list. Oldest submission first by default.
@Getter
@RequiredArgsConstructor
public enum PendingDraftSortField {

    SUBMITTED_AT("submittedAt"),
    UPDATED_AT("updatedAt"),
    STATUS("status");

    private final String property;
}

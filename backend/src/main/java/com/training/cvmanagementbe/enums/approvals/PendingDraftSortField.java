package com.training.cvmanagementbe.enums.approvals;

import lombok.Getter;

// Sortable columns of the admin oversight list. Oldest submission first by default.
@Getter
public enum PendingDraftSortField {

    SUBMITTED_AT("submittedAt"),
    UPDATED_AT("updatedAt"),
    STATUS("status");

    private final String property;

    PendingDraftSortField(String property) {
        this.property = property;
    }
}

package com.training.cvmanagementbe.enums.approvals;

// Sortable columns of the approval queue. Whitelisting them here keeps an arbitrary client string
// out of the ORDER BY and keeps the API name independent of the entity property name.
public enum ApprovalSortField {

    DUE_AT("dueAt"),
    ASSIGNED_AT("assignedAt"),
    LEVEL("level"),
    REVIEW_ROUND("reviewRound");

    private final String property;

    ApprovalSortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}

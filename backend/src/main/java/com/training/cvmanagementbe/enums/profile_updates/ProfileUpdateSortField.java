package com.training.cvmanagementbe.enums.profile_updates;

// Whitelist of sortable columns; the client never sends a raw property name.
public enum ProfileUpdateSortField {

    CREATED_AT("createdAt"),
    REVIEWED_AT("reviewedAt"),
    STATUS("status"),
    ID("id");

    private final String property;

    ProfileUpdateSortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}

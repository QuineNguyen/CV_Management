package com.training.cvmanagementbe.enums;

// Sortable columns of the profile list; maps the API value to the JPA property.
public enum CvProfileSortField {

    NAME("name"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt");

    private final String property;

    CvProfileSortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}

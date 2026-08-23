package com.training.cvmanagementbe.enums;

import java.util.List;
import java.util.stream.Stream;

// Lifecycle of a CV draft. Used as string parameters in native queries.
public enum DraftStatus {

    DRAFT,
    PENDING_TECH_LEAD,
    PENDING_HR,
    PUBLISHED,
    REJECTED,
    CANCELLED;

    // Statuses that must be cancelled when the owner is deactivated.
    public static List<String> openStatusNames() {
        return Stream.of(DRAFT, PENDING_TECH_LEAD, PENDING_HR).map(Enum::name).toList();
    }
}

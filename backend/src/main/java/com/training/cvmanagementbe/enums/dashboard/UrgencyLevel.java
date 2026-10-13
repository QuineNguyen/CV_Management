package com.training.cvmanagementbe.enums.dashboard;

// How close a PENDING update request is to its deadline.
public enum UrgencyLevel {
    APPROACHING,    // due within the next N days, not today
    LAST_DAY,       // due today
    OVERDUE,        // deadline already passed
}

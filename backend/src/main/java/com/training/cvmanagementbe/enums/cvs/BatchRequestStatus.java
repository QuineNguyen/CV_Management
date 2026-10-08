package com.training.cvmanagementbe.enums.cvs;

// PROCESSING -> COMPLETED | COMPLETED_WITH_ERRORS; error_count > 0 <=> COMPLETED_WITH_ERRORS
public enum BatchRequestStatus {
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    CANCELLED
}

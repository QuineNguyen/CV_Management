package com.training.cvmanagementbe.enums.cvs;

// Why an employee is left out of a batch on the preview
public enum BatchExclusionReason {
    SELF_REQUEST,
    ALREADY_PENDING,
    DUPLICATE_IN_BATCH
}

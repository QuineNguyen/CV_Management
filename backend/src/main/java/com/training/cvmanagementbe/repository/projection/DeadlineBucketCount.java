package com.training.cvmanagementbe.repository.projection;

// PENDING update requests split by urgency, read as one aggregate row.
public interface DeadlineBucketCount {

    Long getOverdue();

    Long getLastDay();

    Long getApproaching();
}

package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.enums.cvs.BatchExclusionReason;

import java.util.UUID;

// One resolved recipient; exclusion null means the employee gets a request
public record BatchRecipient(
        UUID employeeId,
        String fullName,
        String departmentName,
        UUID profileId,
        String profileName,
        BatchExclusionReason exclusion
) {
    public boolean included() {
        return exclusion == null;
    }
}

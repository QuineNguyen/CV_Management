package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.users.RequestStatus;

import java.time.LocalDate;
import java.util.UUID;

// Optional list filters; null means "do not filter on this"
public record UpdateRequestCriteria(
        RequestStatus status,
        UUID departmentId,
        Language language,
        LocalDate fromDate,
        LocalDate toDate
) {
}

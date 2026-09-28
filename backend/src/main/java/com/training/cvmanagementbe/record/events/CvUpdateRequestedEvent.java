package com.training.cvmanagementbe.record.events;

import com.training.cvmanagementbe.enums.cvs.Language;

import java.util.UUID;

/*
 * A CV update was requested from an employee.
 * - cvId / profileId decide the link: edit the CV, or create it with what is known preselected.
 */
public record CvUpdateRequestedEvent(
        UUID updateRequestId,
        UUID employeeId,
        UUID cvId,
        UUID profileId,
        Language language,
        String reason
) {
}

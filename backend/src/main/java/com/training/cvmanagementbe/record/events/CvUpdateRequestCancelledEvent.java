package com.training.cvmanagementbe.record.events;

import com.training.cvmanagementbe.enums.cvs.Language;

import java.util.UUID;

/*
 * A PENDING update request was cancelled, by hand or by one of the three
 * automatic paths (CV deleted, profile deleted, employee deactivated).
 * Only the employee is notified; the listener resolves the actor's name after commit.
 */
public record CvUpdateRequestCancelledEvent(
        UUID requestId,
        UUID employeeId,
        UUID cvId,
        UUID profileId,
        Language language,
        String reason,
        UUID actorId
) {
}

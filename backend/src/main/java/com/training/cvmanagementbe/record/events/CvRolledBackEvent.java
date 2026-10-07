package com.training.cvmanagementbe.record.events;

import java.util.UUID;

/*
 * Rollback. Carries both numbers: The owner must be told which version
 * came back and which number it now has.
 */
public record CvRolledBackEvent(
        UUID cvId,
        UUID ownerId,
        UUID actorId,
        int sourceVersionNumber,
        int newVersionNumber
) {
}

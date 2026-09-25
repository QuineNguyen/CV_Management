package com.training.cvmanagementbe.record.events;

import java.util.UUID;

// Published after a profile and its CVs are soft-deleted.
public record CvProfileDeletedEvent(
        UUID profileId,
        UUID ownerId,
        UUID actorId
) {
}

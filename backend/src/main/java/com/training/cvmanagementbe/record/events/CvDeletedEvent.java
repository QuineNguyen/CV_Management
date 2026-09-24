package com.training.cvmanagementbe.record.events;

import java.util.UUID;

// Published after an Admin/HR soft-delete of a CV commits
public record CvDeletedEvent(
        UUID cvId,
        UUID ownerId,
        UUID actorId
) {
}

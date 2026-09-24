package com.training.cvmanagementbe.record.events;

import java.util.UUID;

// Published after a CV restore commits
public record CvRestoredEvent(
        UUID cvId,
        UUID ownerId,
        UUID actorId
) {
}

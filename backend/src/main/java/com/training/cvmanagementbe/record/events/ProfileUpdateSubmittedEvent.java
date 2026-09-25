package com.training.cvmanagementbe.record.events;

import java.util.UUID;

// Published after a personal-info update request is stored
public record ProfileUpdateSubmittedEvent(
        UUID requestId,
        UUID requesterId
) {
}

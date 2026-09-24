package com.training.cvmanagementbe.record.events;

import java.util.UUID;

/*
 * Published after a reviewer decides a personal-info update request.
 * @param reason    verbatim rejection reason; null when approved
 */
public record ProfileUpdateDecidedEvent(
        UUID requestId,
        UUID requesterId,
        UUID reviewerId,
        boolean approved,
        String reason
) {
}

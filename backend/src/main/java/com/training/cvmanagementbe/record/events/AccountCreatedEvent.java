package com.training.cvmanagementbe.record.events;

import java.util.UUID;

/*
 * A new account exists. The temporary password is for the email only;
 * the listener must never put it into the stored in-app row.
 */
public record AccountCreatedEvent(
        UUID userId,
        UUID actorId,
        String temporaryPassword
) {

    // Keeps the password out of logs
    @Override
    public String toString() {
        return "AccountCreatedEvent[userId=%s, actorId=%s]".formatted(userId, actorId);
    }
}

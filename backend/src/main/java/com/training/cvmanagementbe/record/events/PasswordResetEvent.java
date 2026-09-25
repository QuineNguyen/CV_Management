package com.training.cvmanagementbe.record.events;

import java.util.UUID;

/*
 * Published after an admin password reset commits.
 * The temporary password travels to the email only - never into the in-app row.
 */
public record PasswordResetEvent(
        UUID targetUserId,
        UUID actorId,
        String temporaryPassword
) {
    // Keeps the password out of any log line that prints the event.
    @Override
    public String toString() {
        return "PasswordResetEvent[targetUserId=" + targetUserId + ", actorId=" + actorId + "]";
    }
}

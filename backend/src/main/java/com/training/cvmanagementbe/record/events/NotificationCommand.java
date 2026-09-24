package com.training.cvmanagementbe.record.events;

import com.training.cvmanagementbe.enums.notifications.NotificationEventType;

import java.util.Map;
import java.util.UUID;

/*
 * One recipient's pair: the in-app row and its email.
 * @param actorId   who caused the event; the pair is skipped when it is the recipient
 */
public record NotificationCommand(
        UUID recipientId,
        UUID actorId,
        NotificationEventType type,
        String content,
        String link,
        String subject,
        Map<String, Object> templateVars
) {

    public boolean notifiesActor() {
        return actorId != null && actorId.equals(recipientId);
    }
}

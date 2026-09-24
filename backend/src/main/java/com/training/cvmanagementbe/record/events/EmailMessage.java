package com.training.cvmanagementbe.record.events;

import java.util.Map;
import java.util.UUID;

/*
 * Wire contract on email.queue, mirrored in email-service.
 * Adding a field is safe; renaming one breaks the consumer.
 * @param emailLogId    email_logs row the consumer settles
 * @param link          Angular route; email-service prefixes the frontend base URL
 */
public record EmailMessage(
        UUID emailLogId,
        String eventType,
        String recipientEmail,
        String recipientName,
        String subject,
        String templateName,
        String link,
        Map<String, Object> templateVars
) {
}

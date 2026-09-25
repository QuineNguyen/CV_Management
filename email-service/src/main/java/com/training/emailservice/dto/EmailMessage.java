package com.training.emailservice.dto;

import java.util.Map;
import java.util.UUID;

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

package com.training.cvmanagementbe.dto.response.notifications;

import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One feed item.
 * @param id    needed by the client to mark the item read
 * @param link  Angular route relative to the app root
 */
@Schema(name = "NotificationResponse", description = "One feed item")
public record NotificationResponse(
        UUID id,
        NotificationEventType type,
        String content,
        String link,
        boolean read,
        LocalDateTime createdAt
) {
}

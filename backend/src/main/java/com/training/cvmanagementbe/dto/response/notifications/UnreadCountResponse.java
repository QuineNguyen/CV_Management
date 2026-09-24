package com.training.cvmanagementbe.dto.response.notifications;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UnreadCountResponse", description = "Response containing the count of unread notifications")
public record UnreadCountResponse(long count) {
}

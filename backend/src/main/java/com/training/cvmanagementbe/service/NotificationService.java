package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.notifications.NotificationResponse;
import com.training.cvmanagementbe.dto.response.notifications.UnreadCountResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

// In-app feed of the signed-in user. Every method is scoped to CurrentActor.
public interface NotificationService {

    PagedResponse<NotificationResponse> list(boolean unreadOnly, Pageable pageable);

    UnreadCountResponse countUnread();

    // Returns the unread count after the write, so the client badge follows the server.
    UnreadCountResponse markAsRead(UUID id);

    UnreadCountResponse markAllAsRead();
}

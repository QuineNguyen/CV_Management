package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.notifications.NotificationResponse;
import com.training.cvmanagementbe.dto.response.notifications.UnreadCountResponse;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.InAppNotification;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.InAppNotificationRepository;
import com.training.cvmanagementbe.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/*
 * Scoped by recipient inside every query, like the approval queue: the feed is an access
 * boundary, so no id sent by the client can widen it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final InAppNotificationRepository notificationRepository;

    @Override
    public PagedResponse<NotificationResponse> list(boolean unreadOnly, Pageable pageable) {
        UUID recipientId = CurrentActor.requireUserId();

        Page<InAppNotification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalse(recipientId, pageable)
                : notificationRepository.findByRecipientId(recipientId, pageable);

        return PagedResponse.of(page, page.getContent().stream().map(this::toResponse).toList());
    }

    @Override
    public UnreadCountResponse countUnread() {
        return unreadCountOf(CurrentActor.requireUserId());
    }

    /*
     * 404 rather than 403 for someone else's row: the caller must not learn it exists.
     * Idempotent: an already-read row is a no-op, not an error.
     */
    @Override
    @Transactional
    public UnreadCountResponse markAsRead(UUID id) {
        UUID recipientId = CurrentActor.requireUserId();

        if (!notificationRepository.existsByIdAndRecipientId(id, recipientId)) {
            throw new ApiException.NotFoundException("notification", id);
        }
        notificationRepository.markRead(id, recipientId);
        return unreadCountOf(recipientId);
    }

    @Override
    @Transactional
    public UnreadCountResponse markAllAsRead() {
        UUID recipientId = CurrentActor.requireUserId();
        notificationRepository.markAllRead(recipientId);
        return unreadCountOf(recipientId);
    }

    private UnreadCountResponse unreadCountOf(UUID recipientId) {
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndReadFalse(recipientId));
    }

    private NotificationResponse toResponse(InAppNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getContent(),
                notification.getLink(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}

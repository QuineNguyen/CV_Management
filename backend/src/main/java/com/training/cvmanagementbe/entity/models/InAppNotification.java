package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One row of the in-app feed. Written once by NotificationDispatcher; afterward only
 * is_read changes, which is why it carries no audit columns.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private NotificationEventType type;

    @Column(name = "content", nullable = false, updatable = false)
    private String content;

    @Column(name = "link", nullable = false, updatable = false)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.notifications.EmailStatus;
import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Pure delivery log: no body part, no back-reference. Inserted here as PENDING,
 * settled by email-service.
 */
@Entity
@Table(name = "email_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private NotificationEventType eventType;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    // Snapshot at sending time; the user's address may change later
    @Column(name = "recipient_email", nullable = false, updatable = false)
    private String recipientEmail;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmailStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

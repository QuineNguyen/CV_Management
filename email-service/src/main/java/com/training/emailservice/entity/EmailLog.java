package com.training.emailservice.entity;

import com.training.emailservice.enums.EmailStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Shared email_logs table. The main backend inserts the row; this service only settles it.
 * Columns it never writes are read-only and @DynamicUpdate limits each UPDATE to changed columns.
 */
@Entity
@Table(name = "email_logs")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class EmailLog {

    @Id
    private UUID id;

    @Column(name = "event_type", insertable = false, updatable = false)
    private String eventType;

    @Column(name = "recipient_email", insertable = false, updatable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmailStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message")
    private String errorMessage;
}

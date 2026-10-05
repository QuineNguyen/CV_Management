package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.configs.EscalationLevel;
import com.training.cvmanagementbe.enums.configs.ReminderTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/*
 * Exists only to deduplicate: the row itself is the information.
 * Delivery result lives in email_logs. No audit columns - the job has no actor.
 */
@Entity
@Table(name = "reminder_logs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReminderTargetType targetType;

    // Polymorphic: equals recipient_id for SLA_DIGEST
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_level", nullable = false)
    private EscalationLevel escalationLevel;

    @Column(name = "sent_date", nullable = false)
    private LocalDate sentDate;
}

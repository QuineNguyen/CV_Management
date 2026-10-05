package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.ReminderLog;
import com.training.cvmanagementbe.enums.configs.ReminderTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, UUID> {

    // Same columns as uk_reminder_logs_daily
    boolean existsByTargetTypeAndTargetIdAndRecipientIdAndSentDate(
            ReminderTargetType targetType, UUID targetId, UUID recipientId, LocalDate sentDate);
}

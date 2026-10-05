package com.training.cvmanagementbe.record.reminders;

import com.training.cvmanagementbe.enums.configs.EscalationLevel;
import com.training.cvmanagementbe.enums.configs.ReminderTargetType;
import com.training.cvmanagementbe.enums.notifications.NotificationEventType;
import com.training.cvmanagementbe.record.events.NotificationCommand;

import java.time.LocalDate;
import java.util.UUID;

/*
 * One reminder: The reminder_logs key plus the notification pair to dispatch.
 * The recipient is read from the command, so the log row and the email never disagree.
 */
public record ReminderNotice(
        ReminderTargetType targetType,
        UUID targetId,
        EscalationLevel escalationLevel,
        LocalDate sentDate,
        NotificationCommand command
) {
    public UUID recipientId() {
        return command.recipientId();
    }
}

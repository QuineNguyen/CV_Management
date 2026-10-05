package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.ReminderLog;
import com.training.cvmanagementbe.enums.notifications.DispatchOutcome;
import com.training.cvmanagementbe.record.events.DispatchOptions;
import com.training.cvmanagementbe.record.reminders.ReminderNotice;
import com.training.cvmanagementbe.repository.ReminderLogRepository;
import com.training.cvmanagementbe.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/*
 * No surrounding transaction on purpose: The log row commits on its own and the dispatcher
 * runs its own REQUIRES_NEW and never throws, so one broken reminder rolls back nothing else.
 * Log first, dispatch second: The promise is "at most one a day". A failed email
 * is already recorded in email_logs, with the in-app row standing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final ReminderLogRepository reminderLogRepository;
    private final NotificationDispatcher notificationDispatcher;

    @Override
    public DispatchOutcome remind(ReminderNotice notice) {
        if (alreadySent(notice)) {
            return DispatchOutcome.SKIPPED;
        }

        try {
            reminderLogRepository.saveAndFlush(ReminderLog.builder()
                    .targetType(notice.targetType())
                    .targetId(notice.targetId())
                    .recipientId(notice.recipientId())
                    .escalationLevel(notice.escalationLevel())
                    .sentDate(notice.sentDate())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Another run won the race: uk_reminder_logs_daily already holds today's row
            log.warn("{} reminder for {} rejected by reminder_logs; skipped",
                    notice.targetType(), notice.targetId());
            return DispatchOutcome.SKIPPED;
        }

        return notificationDispatcher.dispatch(notice.command(), DispatchOptions.DEFAULT);
    }

    // Readable guard; the unique index is the real one
    private boolean alreadySent(ReminderNotice notice) {
        return reminderLogRepository.existsByTargetTypeAndTargetIdAndRecipientIdAndSentDate(
                notice.targetType(), notice.targetId(), notice.recipientId(), notice.sentDate()
        );
    }
}

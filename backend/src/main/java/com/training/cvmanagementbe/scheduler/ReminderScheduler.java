package com.training.cvmanagementbe.scheduler;

import com.training.cvmanagementbe.enums.configs.SystemConfigKey;
import com.training.cvmanagementbe.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/*
 * Single entry point. Ticks every minute instead of a cron expression because
 * reminder_send_hour lives in the DB and can change at runtime; @Scheduled(cron) is read once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reminder.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final SystemConfigService systemConfigService;
    private final ReminderJob reminderJob;
    private final Clock clock;

    // Day of the last completed run; resets naturally when the date changes
    private final AtomicReference<LocalDate> lastRunDate = new AtomicReference<>();

    @Scheduled(fixedDelayString = "${app.reminder.tick-delay-ms:60000}",
            initialDelayString = "${app.reminder.initial-delay-ms:30000}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // Cheapest check first: Once today's run is done, no query until tomorrow
        if (today.equals(lastRunDate.get())) {
            return;
        }

        try {
            if (!systemConfigService.getBoolean(SystemConfigKey.REMINDER_ENABLED)) {
                return;
            }
            // Read every tick, so a new send hour applies without a restart
            if (now.getHour() != systemConfigService.getHourOfDay(SystemConfigKey.REMINDER_SEND_HOUR)) {
                return;
            }
            reminderJob.execute(now);
            lastRunDate.set(today);
        } catch (RuntimeException ex) {
            // Not marked as run: The next tick in the same hour retries
            log.error("Reminder job failed for {}; retrying on the next tick", today, ex);
        }
    }
}

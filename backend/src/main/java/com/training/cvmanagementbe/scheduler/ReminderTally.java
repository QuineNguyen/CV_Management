package com.training.cvmanagementbe.scheduler;

import com.training.cvmanagementbe.enums.configs.ReminderTargetType;
import com.training.cvmanagementbe.enums.notifications.DispatchOutcome;
import com.training.cvmanagementbe.record.reminders.ReminderStepResult;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.function.Supplier;

// Counts outcomes of one step; a failing reminder is logged and never stops the loop
@Slf4j
final class ReminderTally {

    private final ReminderTargetType type;
    private int sent;
    private int skipped;
    private int failed;

    ReminderTally(ReminderTargetType type) {
        this.type = type;
    }

    void attempt(UUID targetId, Supplier<DispatchOutcome> delivery) {
        try {
            DispatchOutcome outcome = delivery.get();
            if (outcome == DispatchOutcome.QUEUED) {
                sent++;
            } else if (outcome == DispatchOutcome.SKIPPED) {
                skipped++;
            } else {
                failed++;
                log.warn("{} reminder for {} logged but not queued: {}", type, targetId, outcome);
            }
        } catch (RuntimeException ex) {
            failed++;
            log.warn("{} reminder for {} failed", type, targetId, ex);
        }
    }

    ReminderStepResult result() {
        return new ReminderStepResult(sent, skipped, failed);
    }
}

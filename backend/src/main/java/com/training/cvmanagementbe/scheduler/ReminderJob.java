package com.training.cvmanagementbe.scheduler;

import com.training.cvmanagementbe.enums.configs.ReminderTargetType;
import com.training.cvmanagementbe.enums.configs.SystemConfigKey;
import com.training.cvmanagementbe.record.reminders.OverdueAssignment;
import com.training.cvmanagementbe.record.reminders.ReminderStepResult;
import com.training.cvmanagementbe.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/*
 * One daily run. No shared transaction - each reminder commits on its own.
 * No audit log: Reminders change no business data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJob {

    private final SystemConfigService systemConfigService;
    private final UpdateRequestReminderStep updateRequestReminderStep;
    private final OverdueAssignmentFinder overdueAssignmentFinder;
    private final ApprovalReminderStep approvalReminderStep;
    private final SlaDigestStep slaDigestStep;

    // Throws when a step broke, so the scheduler retries; reminder_logs prevents resends
    public void execute(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        int thresholdDays = systemConfigService.getInt(SystemConfigKey.ESCALATION_THRESHOLD_DAYS);
        log.info("Reminder job started for {} (escalation threshold {} days)", today, thresholdDays);

        boolean complete = runStep(ReminderTargetType.UPDATE_REQUEST,
                () -> updateRequestReminderStep.run(today, thresholdDays));

        // One scan feeds both the personal reminders and the digest
        List<OverdueAssignment> overdue = overdueAssignmentFinder.find(now);
        complete &= runStep(ReminderTargetType.APPROVAL_ASSIGNMENT, () -> approvalReminderStep.run(today, overdue));
        complete &= runStep(ReminderTargetType.SLA_DIGEST, () -> slaDigestStep.run(today, overdue));

        if (!complete) {
            throw new IllegalStateException("Reminder job incomplete for " + today);
        }
        log.info("Reminder job finished for {}", today);
    }

    // A broken step is logged and the next one still runs
    private boolean runStep(ReminderTargetType step, Supplier<ReminderStepResult> work) {
        try {
            log.info("{} reminders: {}", step, work.get());
            return true;
        } catch (RuntimeException ex) {
            log.error("{} reminder step failed", step, ex);
            return false;
        }
    }


}

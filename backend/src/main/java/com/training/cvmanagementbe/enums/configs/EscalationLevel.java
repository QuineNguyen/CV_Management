package com.training.cvmanagementbe.enums.configs;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// reminder_logs.escalation_level, stored by constant name
public enum EscalationLevel {

    APPROACHING,
    LAST_DAY,
    OVERDUE,
    // Approval assignments and the digest only
    SLA_OVERDUE;

    /*
     * Update request ladder, in calendar days:
     * > threshold -> none, 1..threshold -> APPROACHING, 0 -> LAST_DAY, < 0 -> OVERDUE.
     */
    public static Optional<EscalationLevel> forDeadline(LocalDate today, LocalDate deadline, int thresholdDays) {
        long daysLeft = ChronoUnit.DAYS.between(today, deadline);
        if (daysLeft < 0) {
            return Optional.of(OVERDUE);
        }
        if (daysLeft == 0) {
            return Optional.of(LAST_DAY);
        }
        return daysLeft <= thresholdDays ? Optional.of(APPROACHING) : Optional.empty();
    }

    // Rendered in the highlighted box of the email
    public boolean urgent() {
        return this != APPROACHING;
    }
}

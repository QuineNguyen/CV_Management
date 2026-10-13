package com.training.cvmanagementbe.record.dashboard;

import com.training.cvmanagementbe.enums.dashboard.UrgencyLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/*
 * Bucket boundaries for one dashboard request, fixed at the moment it is computed.
 *
 * - Calendar days, because a deadline means 23:59:59 of the chosen day:
 *      OVERDUE     deadline < now
 *      LAST_DAY    now <= deadline < start of tomorrow
 *      APPROACHING start of tomorrow <= deadline < horizon (start of today + N + 1)
 * - Counts and rows both read these bounds, so a row never carries a badge its count disagrees with.
 */
public record DeadlineWindow(LocalDateTime now,
                             LocalDateTime lastDayEnd,
                             LocalDateTime horizon,
                             int approachingDays) {

    public static DeadlineWindow of(LocalDateTime now, int approachingDays) {
        LocalDate today = now.toLocalDate();
        return new DeadlineWindow(
                now,
                today.plusDays(1).atStartOfDay(),
                today.plusDays(approachingDays + 1L).atStartOfDay(),
                approachingDays
        );
    }

    // Only called for rows already inside the horizon
    public UrgencyLevel classify (LocalDateTime deadline) {
        if (deadline.isBefore(now)) {
            return UrgencyLevel.OVERDUE;
        }
        if (deadline.isBefore(lastDayEnd)) {
            return UrgencyLevel.LAST_DAY;
        }
        return UrgencyLevel.APPROACHING;
    }

    // Positive = days past the deadline day, 0 = due today, negative = days left
    public long daysOverdue(LocalDateTime deadline) {
        return ChronoUnit.DAYS.between(deadline.toLocalDate(), now.toLocalDate());
    }
}

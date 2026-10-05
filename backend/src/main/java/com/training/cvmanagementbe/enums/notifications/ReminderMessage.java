package com.training.cvmanagementbe.enums.notifications;

import com.training.cvmanagementbe.enums.configs.EscalationLevel;
import lombok.RequiredArgsConstructor;

/*
 * Subject and in-app text of every reminder.
 * Arguments are positional and shared by subject and content.
 */
@RequiredArgsConstructor
public enum ReminderMessage {

    // 1 = action (update/create), 2 = target, 3 = deadline, 4 = day count
    UPDATE_REQUEST_APPROACHING(
            "CV update due in %4$d day(s)",
            "Reminder: please %1$s %2$s by %3$s - %4$d day(s) left"
    ),
    UPDATE_REQUEST_LAST_DAY(
            "CV update due today",
            "Today is the deadline to %1$s %2$s"
    ),
    UPDATE_REQUEST_OVERDUE(
            "CV update overdue by %4$d day(s)",
            "The request to %1$s %2$s was due on %3$s and is %4$d day(s) overdue"
    ),

    // 1 = CV owner, 2 = CV label, 3 = review level, 4 = overdue text
    APPROVAL_SLA_OVERDUE(
            "CV review overdue - %1$s",
            "Your %3$s of $1$s's CV %2$s is %4$s past its deadline"
    ),

    // 1 = overdue count, 2 = oldest overdue text
    SLA_DIGEST(
            "SLA digest - %1$d overdue review(s)",
            "%1$d approval assignment(s) are past their deadline; the oldest by %2$s"
    );

    private final String subjectPattern;
    private final String contentPattern;

    public String subject(Object... args) {
        return subjectPattern.formatted(args);
    }

    public String content(Object... args) {
        return contentPattern.formatted(args);
    }

    public static ReminderMessage forUpdateRequest(EscalationLevel level) {
        return switch (level) {
            case APPROACHING -> UPDATE_REQUEST_APPROACHING;
            case LAST_DAY -> UPDATE_REQUEST_LAST_DAY;
            case OVERDUE -> UPDATE_REQUEST_OVERDUE;
            case SLA_OVERDUE -> throw new IllegalArgumentException("SLA_OVERDUE is not an update request level");
        };
    }
}

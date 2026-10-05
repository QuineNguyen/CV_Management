package com.training.cvmanagementbe.scheduler;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Display helpers, worded like NotificationListener so reminders read like every other notice
final class ReminderFormats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm");
    private static final String UNKNOWN_PERSON = "Someone";
    private static final String UNKNOWN_VALUE = "-";
    private static final String TECHNICAL_REVIEW = "technical review";
    private static final String FORMAT_REVIEW = "format review";

    private ReminderFormats() {

    }

    static String date(LocalDateTime value) {
        return value == null ? null : DATE.format(value);
    }

    static String dateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    static String orUnknownPerson(String name) {
        return name == null ? UNKNOWN_PERSON : name;
    }

    static String orUnknownValue(String value) {
        return value == null ? UNKNOWN_VALUE : value;
    }

    static String levelLabel(ApprovalLevel level) {
        return level == ApprovalLevel.LEVEL_1 ? TECHNICAL_REVIEW : FORMAT_REVIEW;
    }

    // Calendar days; 0 means the deadline passed earlier today
    static String overdueText(long days) {
        if (days <= 0) {
            return "less than a day";
        }
        return days == 1 ? "1 day" : days + " days";
    }
}

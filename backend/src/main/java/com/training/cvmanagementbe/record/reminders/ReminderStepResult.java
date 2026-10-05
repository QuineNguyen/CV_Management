package com.training.cvmanagementbe.record.reminders;

// Outcome counts of one step, logged by ReminderJob
public record ReminderStepResult(int sent, int skipped, int failed) {
}

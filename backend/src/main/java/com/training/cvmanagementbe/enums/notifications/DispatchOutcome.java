package com.training.cvmanagementbe.enums.notifications;

// What happened to one notification pair; the batch worker counts FAILED right away
public enum DispatchOutcome {
    // Email handed to the broker
    QUEUED,
    // Nothing to send: The recipient caused the event
    SKIPPED,
    // Not stored or not queued; its email_logs row, if any, is FAILED already
    FAILED
}

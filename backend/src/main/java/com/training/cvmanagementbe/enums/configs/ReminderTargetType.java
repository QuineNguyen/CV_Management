package com.training.cvmanagementbe.enums.configs;

// reminder_logs.target_type, stored by constant name
public enum ReminderTargetType {

    UPDATE_REQUEST,
    APPROVAL_ASSIGNMENT,
    // target_id = recipient_id: one digest per recipient per day
    SLA_DIGEST
}

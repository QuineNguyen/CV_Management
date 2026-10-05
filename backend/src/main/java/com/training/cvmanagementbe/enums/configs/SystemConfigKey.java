package com.training.cvmanagementbe.enums.configs;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
 * Keys of system_configs and the fallback used when a row is missing or unreadable.
 * Defaults: 9:00, enabled, 3 days, 3 days.
 */
@Getter
@RequiredArgsConstructor
public enum SystemConfigKey {

    REMINDER_ENABLED("reminder_enabled", "true"),
    REMINDER_SEND_HOUR("reminder_send_hour", "9"),
    ESCALATION_THRESHOLD_DAYS("escalation_threshold_days", "3"),
    APPROVAL_SLA_DAYS("approval_sla_days", "3");

    // Value of system_configs.config_key
    private final String key;
    private final String defaultValue;
}

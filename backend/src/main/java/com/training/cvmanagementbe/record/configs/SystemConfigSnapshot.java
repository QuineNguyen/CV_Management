package com.training.cvmanagementbe.record.configs;

// old_value / new_value payload of a system config audit entry
public record SystemConfigSnapshot(String configKey, String configValue) {
}

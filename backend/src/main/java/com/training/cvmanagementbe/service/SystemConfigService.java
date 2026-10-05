package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.enums.configs.SystemConfigKey;

public interface SystemConfigService {

    // Raw value or the key's default when the row is missing or blank
    String getString(SystemConfigKey key);

    int getInt(SystemConfigKey key);

    boolean getBoolean(SystemConfigKey key);

    // Accepts "9" or "09:00"; falls back to the default outside 0-23
    int getHourOfDay(SystemConfigKey key);
}

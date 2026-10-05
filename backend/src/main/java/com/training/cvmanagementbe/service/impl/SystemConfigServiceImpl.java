package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.SystemConfig;
import com.training.cvmanagementbe.enums.configs.SystemConfigKey;
import com.training.cvmanagementbe.repository.SystemConfigRepository;
import com.training.cvmanagementbe.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/*
 * Reads system_configs on every call, no cache: at ~50 users the cost is nothing and an
 * admin change applies on the next tick without a restart. Bad values never throw.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("H:mm");
    private static final String TIME_SEPARATOR = ":";
    private static final int MAX_HOUR = 23;

    private final SystemConfigRepository systemConfigRepository;

    @Override
    public String getString(SystemConfigKey key) {
        return systemConfigRepository.findByConfigKey(key.getKey())
                .map(SystemConfig::getConfigValue)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(key.getDefaultValue());
    }

    @Override
    public int getInt(SystemConfigKey key) {
        String raw = getString(key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallbackInt(key, raw);
        }
    }

    // Strict on purpose: Boolean.parseBoolean would turn a typo into "disabled"
    @Override
    public boolean getBoolean(SystemConfigKey key) {
        String raw = getString(key);
        if (Boolean.TRUE.toString().equalsIgnoreCase(raw)) {
            return true;
        }
        if (Boolean.FALSE.toString().equalsIgnoreCase(raw)) {
            return false;
        }
        log.warn("Invalid value '{}' for system config '{}'; using default {}",
                raw, key.getKey(), key.getDefaultValue());
        return Boolean.parseBoolean(key.getDefaultValue());
    }

    @Override
    public int getHourOfDay(SystemConfigKey key) {
        String raw = getString(key);
        try {
            int hour = raw.contains(TIME_SEPARATOR)
                    ? LocalTime.parse(raw, HOUR_MINUTE).getHour()
                    : Integer.parseInt(raw);
            if (hour >= 0 && hour <= MAX_HOUR) {
                return hour;
            }
        } catch (NumberFormatException | DateTimeException ignored) {
            // Falls through to the default below
        }
        return fallbackInt(key, raw);
    }

    private int fallbackInt(SystemConfigKey key, String raw) {
        log.warn("Invalid value '{}' for system config '{}'; using default {}",
                raw, key.getKey(), key.getDefaultValue());
        return Integer.parseInt(key.getDefaultValue());
    }
}

package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.constant.SystemConfigLimits;
import com.training.cvmanagementbe.dto.request.configs.UpdateSystemConfigRequest;
import com.training.cvmanagementbe.dto.response.configs.SystemConfigResponse;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.SystemConfig;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.configs.SystemConfigKey;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.configs.SystemConfigSnapshot;
import com.training.cvmanagementbe.repository.SystemConfigRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    // Keys edited on the settings screen
    private static final Set<SystemConfigKey> EDITABLE_KEYS = EnumSet.of(
            SystemConfigKey.REMINDER_SEND_HOUR,
            SystemConfigKey.REMINDER_ENABLED,
            SystemConfigKey.ESCALATION_THRESHOLD_DAYS,
            SystemConfigKey.APPROVAL_SLA_DAYS
    );

    private final SystemConfigRepository systemConfigRepository;
    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    // ---------- Typed reads ----------

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
        return fallbackBoolean(key, raw);
    }

    @Override
    public int getHourOfDay(SystemConfigKey key) {
        String raw = getString(key);
        try {
            int hour = raw.contains(TIME_SEPARATOR)
                    ? LocalTime.parse(raw, HOUR_MINUTE).getHour()
                    : Integer.parseInt(raw);
            if (hour >= SystemConfigLimits.MIN_HOUR && hour <= SystemConfigLimits.MAX_HOUR) {
                return hour;
            }
        } catch (NumberFormatException | DateTimeException ignored) {
            // Falls through to the default below
        }
        return fallbackInt(key, raw);
    }

    // ---------- Settings screen ----------

    @Override
    public SystemConfigResponse getConfigs() {
        requireAdmin();
        return toResponse();
    }

    /*
     * Writes and audits only the keys whose effective value changed, so "9:00" stored by hand and
     * 9 submitted from the form count as the same value.
     * approval_assignments.due_at is deliberately left alone: It was fixed when each assignment
     * was created, so a new SLA only reaches assignments created from now on.
     */
    @Override
    @Transactional
    public SystemConfigResponse updateConfigs(UpdateSystemConfigRequest request) {
        requireAdmin();
        UUID actorId = CurrentActor.requireUserId();

        Map<SystemConfigKey, String> current = effectiveValues();
        Map<String, SystemConfig> rows = loadRows();

        requestedValues(request).forEach((key, value) -> {
            if (!value.equals(current.get(key))) {
                write(key, value, rows.get(key.getKey()), actorId);
            }
        });
        return toResponse();
    }

    // ---------- Helpers ----------

    private void write(SystemConfigKey key, String value, SystemConfig row, UUID actorId) {
        if (row == null) {
            SystemConfig created = systemConfigRepository.save(SystemConfig.create(key.getKey(), value, actorId));
            auditLogger.record(Action.UPDATE_SYSTEM_CONFIG, TargetType.SYSTEM_CONFIG, created.getId(),
                    null, new SystemConfigSnapshot(key.getKey(), value));
            return;
        }

        SystemConfigSnapshot before = new SystemConfigSnapshot(key.getKey(), row.getConfigValue());
        // Managed entity: Flushed with the transaction
        row.updateValue(value, actorId);
        auditLogger.record(Action.UPDATE_SYSTEM_CONFIG, TargetType.SYSTEM_CONFIG, row.getId(),
                before, new SystemConfigSnapshot(key.getKey(), value));
    }

    // Same parsing as the scheduler, so the screen shows what actually runs
    private Map<SystemConfigKey, String> effectiveValues() {
        Map<SystemConfigKey, String> values = new EnumMap<>(SystemConfigKey.class);
        values.put(SystemConfigKey.REMINDER_SEND_HOUR, String.valueOf(getHourOfDay(SystemConfigKey.REMINDER_SEND_HOUR)));
        values.put(SystemConfigKey.REMINDER_ENABLED, String.valueOf(getBoolean(SystemConfigKey.REMINDER_ENABLED)));
        values.put(SystemConfigKey.ESCALATION_THRESHOLD_DAYS, String.valueOf(getInt(SystemConfigKey.ESCALATION_THRESHOLD_DAYS)));
        values.put(SystemConfigKey.APPROVAL_SLA_DAYS, String.valueOf(getInt(SystemConfigKey.APPROVAL_SLA_DAYS)));
        return values;
    }

    private static Map<SystemConfigKey, String> requestedValues(UpdateSystemConfigRequest request) {
        Map<SystemConfigKey, String> values = new EnumMap<>(SystemConfigKey.class);
        values.put(SystemConfigKey.REMINDER_SEND_HOUR, String.valueOf(request.reminderSendHour()));
        values.put(SystemConfigKey.REMINDER_ENABLED, String.valueOf(request.reminderEnabled()));
        values.put(SystemConfigKey.ESCALATION_THRESHOLD_DAYS, String.valueOf(request.escalationThresholdDays()));
        values.put(SystemConfigKey.APPROVAL_SLA_DAYS, String.valueOf(request.approvalSlaDays()));
        return values;
    }

    private Map<String, SystemConfig> loadRows() {
        List<String> keys = EDITABLE_KEYS.stream().map(SystemConfigKey::getKey).toList();
        return systemConfigRepository.findByConfigKeyIn(keys).stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, Function.identity()));
    }

    private SystemConfigResponse toResponse() {
        Optional<SystemConfig> latest = loadRows().values().stream()
                .max(Comparator.comparing(SystemConfig::getUpdatedAt));
        String updatedByName = latest
                .flatMap(row -> userRepository.findById(row.getUpdatedBy()))
                .map(User::getFullName)
                .orElse(null);

        return new SystemConfigResponse(
                getHourOfDay(SystemConfigKey.REMINDER_SEND_HOUR),
                getBoolean(SystemConfigKey.REMINDER_ENABLED),
                getInt(SystemConfigKey.ESCALATION_THRESHOLD_DAYS),
                getInt(SystemConfigKey.APPROVAL_SLA_DAYS),
                updatedByName,
                latest.map(SystemConfig::getUpdatedAt).orElse(null)
        );
    }

    private void requireAdmin() {
        if (CurrentActor.requireRole() != Role.ADMIN) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
    }

    private boolean fallbackBoolean(SystemConfigKey key, String raw) {
        logInvalidValue(key, raw);
        return Boolean.parseBoolean(key.getDefaultValue());
    }

    private int fallbackInt(SystemConfigKey key, String raw) {
        logInvalidValue(key, raw);
        return Integer.parseInt(key.getDefaultValue());
    }

    private void logInvalidValue(SystemConfigKey key, String raw) {
        log.warn("Invalid value '{}' for system config '{}'; using default {}",
                raw, key.getKey(), key.getDefaultValue());
    }
}

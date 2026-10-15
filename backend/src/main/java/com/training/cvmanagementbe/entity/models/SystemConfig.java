package com.training.cvmanagementbe.entity.models;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One row per setting; the value is a string parsed by SystemConfigService.
 */
@Entity
@Table(name = "system_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Recreates a row the seed should have inserted; never expected in a healthy database
    public static SystemConfig create(String configKey, String configValue, UUID actorId) {
        SystemConfig config = new SystemConfig();
        config.configKey = configKey;
        config.updateValue(configValue, actorId);
        return config;
    }

    // The caller passes an already validated value
    public void updateValue(String newValue, UUID actorId) {
        this.configValue = newValue;
        this.updatedBy = actorId;
        this.updatedAt = LocalDateTime.now();
    }
}

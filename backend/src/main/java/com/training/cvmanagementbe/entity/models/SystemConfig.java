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
}

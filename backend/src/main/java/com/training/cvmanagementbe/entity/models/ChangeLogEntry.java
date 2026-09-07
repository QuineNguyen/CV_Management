package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.entity.converter.CvSectionKeyConverter;
import com.training.cvmanagementbe.enums.ChangeType;
import com.training.cvmanagementbe.enums.CvSectionKey;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/*
 * One change of a version against its predecessor, at (section, item, field) granularity.
 *
 * - itemId and fieldKey are both optional on purpose: SINGLE sections
 * have no items, so requiring itemId would make them unmodifiable.
 */
@Entity
@Table(name = "change_log_entries")
@Getter
@Setter
public class ChangeLogEntry {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "version_id", updatable = false, nullable = false)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", updatable = false, nullable = false, length = 40)
    private ChangeType changeType;

    // Stored as the snake_case key, matching content_json and the column's CHECK constraint.
    @Convert(converter = CvSectionKeyConverter.class)
    @Column(name = "section_key", updatable = false, nullable = false, length = 40)
    private CvSectionKey sectionKey;

    // Null for SINGLE sections.
    @Column(name = "item_id", updatable = false, length = 64)
    private String itemId;

    // Null when the whole item was added or removed.
    @Column(name = "field_key", updatable = false, length = 128)
    private String fieldKey;

    @Column(name = "old_value", updatable = false, columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", updatable = false, columnDefinition = "TEXT")
    private String newValue;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}

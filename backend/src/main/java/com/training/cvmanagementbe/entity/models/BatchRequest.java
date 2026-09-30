package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.cvs.BatchRequestStatus;
import com.training.cvmanagementbe.enums.cvs.BatchTargetType;
import com.training.cvmanagementbe.enums.cvs.Language;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/*
 * One batch of update requests.
 * Counters are moved by bulk updates in BatchProgressRecorder, never through these setters,
 * so the worker and the failure callbacks cannot overwrite each other.
 */
@Getter
@Setter
@Entity
@Table(name = "batch_requests")
public class BatchRequest extends BaseEntity {

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    // Always 23:59:59 of the chosen day
    @Column(name = "deadline", nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 40)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private BatchTargetType targetType;

    // JSON array of department / team / employee ids
    @Column(name = "target_value", nullable = false, columnDefinition = "LONGTEXT")
    private String targetValue;

    // Locked after exclusion
    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private BatchRequestStatus status;
}

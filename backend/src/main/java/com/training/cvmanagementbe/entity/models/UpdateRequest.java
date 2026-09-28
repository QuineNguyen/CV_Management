package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * A request from Admin/HR asking one employee to update one CV slot (profile x language).
 * - profile_id and cv_id may both be empty; the auto-link on CV creation fills
 * them later in one write.
 * - uk_pending_key is a DB-generated column. It is deliberately not
 * mapped: Hibernate would otherwise try to write it.
 */
@Entity
@Table(name = "update_requests")
@Getter
@Setter
public class UpdateRequest extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    // Query-only: lets the list filter by department and sort by name. Never written through
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", insertable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private User employee;

    @Column(name = "profile_id")
    private UUID profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private Language language;

    @Column(name = "cv_id")
    private UUID cvId;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    // JSON array of {section_key, item_id, field_key, note}; read and written via AnchoredNoteCodec
    @Column(name = "anchored_notes", columnDefinition = "LONGTEXT")
    private String anchoredNotes;

    // Always 23:59:59 of the chosen day
    @Column(name = "deadline", nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    // Null for a single request
    @Column(name = "batch_request_id")
    private UUID batchRequestId;

    @Column(name = "notification_failed", nullable = false)
    private boolean notificationFailed;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}

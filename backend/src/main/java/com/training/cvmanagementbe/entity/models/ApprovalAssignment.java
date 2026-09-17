package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.ApprovalLevel;
import com.training.cvmanagementbe.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One draft handed to one approver at one level.
 * - Rows are append-only: reassigning closes the current row as REASSIGNED and
 * inserts a new one, so the handover chain is rebuilt by ordering on assignedAt within
 * (draftId, level, reviewRound) group. That is why there is no nextAssignmentId
 * pointer.
 * - Extends CreationOnlyEntity: the row is written once and afterward only closed, so an
 * updated_by column would record the person who closed it, not an editor.
 */
@Entity
@Table(name = "approval_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalAssignment {

    // Open work of one draft. At most one row per draft sits here at a time.
    public static final AssignmentStatus OPEN_STATUS = AssignmentStatus.ASSIGNED;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    // Stored as the integer the spec and the reports speak in; approvalLevel() is the
    // accessor callers should use so no comparison against a bare 1 or 2 ever appears.
    @Column(name = "level", nullable = false, columnDefinition = "SMALLINT")
    private int level;

    // Null only when status = SKIPPED - nobody was asked to review.
    @Column(name = "assignee_id")
    private UUID assigneeId;

    // Null when the system assigned. Set only on the Admin reassign path.
    @Column(name = "assigned_by")
    private UUID assignedBy;

    // Snapshot of cv_drafts.review_round at assignment time.
    @Column(name = "review_round", nullable = false, columnDefinition = "SMALLINT")
    private int reviewRound;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AssignmentStatus status;

    /*
     * Why this person - or why nobody. Never null: team membership is not time-versioned,
     * so this text is the only thing that can still explain a past round once the employee moves team.
     */
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    // assignedAt + SLA. Frozen per row, so changing the SLA never moves an existing due
    // date - it only applies to assignments created afterward.
    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    // Set when the row leaves ASSIGNED, whichever terminal status it reaches.
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // ---------- Derived ----------

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    @Transient
    public ApprovalLevel approvalLevel() {
        return ApprovalLevel.of(level);
    }

    @Transient
    public boolean isOpen() {
        return status == OPEN_STATUS;
    }

    // True once the SLA has run out on an assignment nobody has acted on yet.
    @Transient
    public boolean isOverdue(LocalDateTime now) {
        return isOpen() && dueAt != null && dueAt.isBefore(now);
    }
}

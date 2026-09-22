package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.approvals.ApprovalLevel;
import com.training.cvmanagementbe.enums.approvals.DecisionResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One recorded verdict. Never overwritten: a draft that went through three rounds
 * keeps three rows per level and the rejection stays readable after the CV is published.
 * - Not a BaseEntity: decidedAt and approverId already say who wrote the
 * row and when, so audit columns would duplicate them.
 */
@Entity
@Table(name = "approval_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "level", nullable = false, columnDefinition = "SMALLINT")
    private int level;

    // Which round produced the verdict - the same (draft, level) pair has one row per round.
    @Column(name = "review_round", nullable = false, columnDefinition = "SMALLINT")
    private int reviewRound;

    // Null when result = SKIPPED: no human decided.
    @Column(name = "approver_id")
    private UUID approverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 40)
    private DecisionResult result;

    // Mandatory for REJECTED and SKIPPED; optional for APPROVED.
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    @Transient
    public ApprovalLevel approvalLevel() {
        return ApprovalLevel.of(level);
    }
}

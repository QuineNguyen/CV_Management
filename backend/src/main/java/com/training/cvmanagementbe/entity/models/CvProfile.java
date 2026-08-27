package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.LifecycleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One professional persona of an employee ("Software Developer", "AI Engineer").
 *
 * - Deleting is soft: the row stays so its CVs and their approval history remain readable.
 * A deleted profile also stops occupying its name, which is what lets a restore be renamed.
 */
@Entity
@Table(name = "cv_profiles")
@Getter
@Setter
public class CvProfile extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    // Exactly one ACTIVE profile per employee carries this flag.
    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    // Must be a team the employee belongs to; priority #1 when picking the level-1 reviewer.
    @Column(name = "linked_team_id", nullable = false)
    private UUID linkedTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.ACTIVE;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

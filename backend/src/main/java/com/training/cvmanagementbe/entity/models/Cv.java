package com.training.cvmanagementbe.entity.models;

import com.training.cvmanagementbe.enums.Language;
import com.training.cvmanagementbe.enums.LifecycleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/*
 * One CV = one employee x one profile x one language
 *
 * - The row is never overwritten by an edit: accepted content becomes a new CvVersion
 * What lives here is identity and lifecycle only.
 */
@Entity
@Table(name = "cvs")
@Getter
@Setter
public class Cv extends BaseEntity {

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    // ORDINAL would silently reshuffle every stored row the day the enum is reordered.
    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 40)
    private Language language;

    /*
     * Null means this row IS the master of its profile - there is no is_master column,
     * because two columns encoding one binary fact can contradict each other. Always points at a
     * CV of the same profile.
     */
    @Column(name = "master_cv_id")
    private UUID masterCvId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 40)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.ACTIVE;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Transient
    public boolean isMaster() {
        return masterCvId == null;
    }
}

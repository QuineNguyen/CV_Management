package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.CvProfile;
import com.training.cvmanagementbe.enums.LifecycleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvProfileRepository extends JpaRepository<CvProfile, UUID> {

    Page<CvProfile> findByEmployeeIdAndLifecycleStatus(UUID employeeId,
                                                       LifecycleStatus lifecycleStatus,
                                                       Pageable pageable);

    List<CvProfile> findByEmployeeIdAndLifecycleStatus(UUID employeeId, LifecycleStatus lifecycleStatus);

    Optional<CvProfile> findByIdAndLifecycleStatus(UUID id, LifecycleStatus lifecycleStatus);

    Optional<CvProfile> findByEmployeeIdAndPrimaryTrueAndLifecycleStatus(UUID employeeId,
                                                                         LifecycleStatus lifecycleStatus);

    boolean existsByEmployeeIdAndLifecycleStatus(UUID employeeId, LifecycleStatus lifecycleStatus);

    boolean existsByEmployeeIdAndNameAndLifecycleStatus(UUID employeeId,
                                                        String name,
                                                        LifecycleStatus lifecycleStatus);

    boolean existsByEmployeeIdAndNameAndLifecycleStatusAndIdNot(UUID employeeId,
                                                                String name,
                                                                LifecycleStatus lifecycleStatus,
                                                                UUID excludeId);

    // ---------- Cross-aggregate reads ----------
    // cvs and cv_drafts have no entity yet (later phases), so these stay native.

    // Active CVs inside a profile; shown as a column and read by the delete guard.
    @Query(value = """
            SELECT COUNT(1) FROM cvs
            WHERE profile_id = :profileId
              AND lifecycle_status = 'ACTIVE'
            """, nativeQuery = true)
    int countActiveCvsByProfileId(@Param("profileId") UUID profileId);

    // A profile with a CV awaiting approval cannot be deleted; the approval must be cancelled first.
    @Query(value = """
            SELECT COUNT(1) FROM cv_drafts d
            JOIN cvs c ON d.cv_id = c.id
            WHERE c.profile_id = :profileId
              AND c.lifecycle_status = 'ACTIVE'
              AND d.status IN ('PENDING_TECH_LEAD', 'PENDING_HR')
            """, nativeQuery = true)
    long countPendingDraftsByProfileId(@Param("profileId") UUID profileId);

    // Deleting a profile soft-deletes its CVs in the same transaction.
    @Modifying
    @Query(value = """
            UPDATE cvs
            SET lifecycle_status = 'DELETED',
                deleted_by = :actorId,
                deleted_at = :deletedAt,
                updated_by = :actorId,
                updated_at = :deletedAt
            WHERE profile_id = :profileId
              AND lifecycle_status = 'ACTIVE'
            """, nativeQuery = true)
    int softDeleteCvsByProfileId(@Param("profileId") UUID profileId,
                                 @Param("actorId") UUID actorId,
                                 @Param("deletedAt") LocalDateTime deletedAt);
}

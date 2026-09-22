package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.Cv;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvRepository extends JpaRepository<Cv, UUID> {

    // ---------- Lookups ----------

    Optional<Cv> findByIdAndLifecycleStatus(UUID id, LifecycleStatus lifecycleStatus);

    List<Cv> findByProfileIdAndLifecycleStatusOrderByLanguageAsc(UUID profileId, LifecycleStatus lifecycleStatus);

    List<Cv> findByProfileIdOrderByLanguageAsc(UUID profileId);

    Optional<Cv> findByProfileIdAndLanguageAndLifecycleStatus(
            UUID profileId, Language language, LifecycleStatus lifecycleStatus);

    boolean existsByProfileIdAndLanguageAndLifecycleStatus(
            UUID profileId, Language language, LifecycleStatus lifecycleStatus);

    /** The master is the ACTIVE row whose master_cv_id is null. */
    Optional<Cv> findByProfileIdAndMasterCvIdIsNullAndLifecycleStatus(
            UUID profileId, LifecycleStatus lifecycleStatus);

    List<Cv> findByMasterCvIdAndLifecycleStatus(UUID masterCvId, LifecycleStatus lifecycleStatus);

    Page<Cv> findByLifecycleStatus(LifecycleStatus lifecycleStatus, Pageable pageable);

    /*
     * Row lock taken by VersionPublisher before reading MAX(version_number). Without it two
     * concurrent publishes read the same maximum and one of them dies on the unique key instead
     * of simply queueing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cv c WHERE c.id = :id")
    Optional<Cv> findByIdForUpdate(@Param("id") UUID id);

    // ---------- update_requests (no entity yet) ----------

    /*
     * Auto-link. Two things this statement must get right:
     *
     * - cv_id and profile_id are filled together, because the composite FK
     *   (cv_id, profile_id) -> cvs(id, profile_id) is only checked when both are non-null.
     *   Filling them in two statements leaves a window where neither is checked.
     *
     * - A request that already names this profile wins over one that left profile_id empty,
     *   regardless of age. Filling in the empty one first would produce two PENDING requests for
     *   the same (employee, profile, language) and the employee
     *   gets two emails demanding the same work.
     *
     * MariaDB will not read the target table directly in a subquery, hence the derived table.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE update_requests
               SET cv_id = :cvId,
                   profile_id = :profileId,
                   updated_by = :actorId,
                   updated_at = :now
             WHERE id = (
                   SELECT chosen.id FROM (
                       SELECT r.id FROM update_requests r
                        WHERE r.employee_id = :employeeId
                          AND r.language = :language
                          AND r.status = 'PENDING'
                          AND r.cv_id IS NULL
                          AND (r.profile_id = :profileId OR r.profile_id IS NULL)
                        ORDER BY (r.profile_id IS NULL) ASC, r.created_at ASC
                        LIMIT 1
                   ) AS chosen
             )
            """, nativeQuery = true)
    int linkOldestPendingRequest(@Param("cvId") UUID cvId,
                                 @Param("profileId") UUID profileId,
                                 @Param("employeeId") UUID employeeId,
                                 @Param("language") String language,
                                 @Param("actorId") UUID actorId,
                                 @Param("now") LocalDateTime now);

    /** Publishing content is what completes a request — not merely creating the CV. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE update_requests
               SET status = 'COMPLETED',
                   completed_at = :completedAt,
                   updated_by = :actorId,
                   updated_at = :completedAt
             WHERE cv_id = :cvId AND status = 'PENDING'
            """, nativeQuery = true)
    int completePendingRequestsByCvId(@Param("cvId") UUID cvId,
                                      @Param("actorId") UUID actorId,
                                      @Param("completedAt") LocalDateTime completedAt);

    /** A request pointing at a deleted CV can never be answered, so it stops asking. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE update_requests
               SET status = 'CANCELLED',
                   cancelled_by = :actorId,
                   cancelled_at = :cancelledAt,
                   updated_by = :actorId,
                   updated_at = :cancelledAt
             WHERE cv_id = :cvId AND status = 'PENDING'
            """, nativeQuery = true)
    int cancelPendingRequestsByCvId(@Param("cvId") UUID cvId,
                                    @Param("actorId") UUID actorId,
                                    @Param("cancelledAt") LocalDateTime cancelledAt);

    /*
     * Every stored snapshot of this profile, versions and drafts alike. Small by construction: a
     * profile holds at most three CVs, and this runs once per save.
     */
    @Query(value = """
            SELECT v.content_json FROM cv_versions v
              JOIN cvs c ON c.id = v.cv_id
             WHERE c.profile_id = :profileId
             UNION ALL
            SELECT d.content_json FROM cv_drafts d
              JOIN cvs c ON c.id = d.cv_id
             WHERE c.profile_id = :profileId
            """, nativeQuery = true)
    List<String> findContentSnapshotsByProfileId(@Param("profileId") UUID profileId);

    /*
     * Whether one item_id already lives under a different profile.
     *
     * A LIKE over content_json rather than an indexed lookup: item ids are embedded in the JSON by
     * design, so there is no column to index. Only ids the profile does not already own
     * are checked — the handful just added, not every entry in the CV.
     *
     * Returns a row count rather than EXISTS: MariaDB answers EXISTS with 1/0 as an integer, which
     * Spring Data cannot hand back as a Boolean.
     *
     * Matches the bare UUID, not '"item_id":"<id>"'. A UUID is specific enough on its own, and
     * pinning the key name would tie this to Jackson emitting no space after the colon — a
     * formatting setting that, if it ever changed, would make every check silently pass.
     */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT 1 FROM cv_versions v
                  JOIN cvs c ON c.id = v.cv_id
                 WHERE c.profile_id <> :profileId
                   AND v.content_json LIKE CONCAT('%', :itemId, '%')
                 LIMIT 1
            ) AS found
            """, nativeQuery = true)
    long countItemIdInVersionsOutsideProfile(@Param("profileId") UUID profileId,
                                             @Param("itemId") String itemId);

    /** Same check against open and closed drafts; a draft holds ids no version has yet. */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT 1 FROM cv_drafts d
                  JOIN cvs c ON c.id = d.cv_id
                 WHERE c.profile_id <> :profileId
                   AND d.content_json LIKE CONCAT('%', :itemId, '%')
                 LIMIT 1
            ) AS found
            """, nativeQuery = true)
    long countItemIdInDraftsOutsideProfile(@Param("profileId") UUID profileId,
                                           @Param("itemId") String itemId);
}

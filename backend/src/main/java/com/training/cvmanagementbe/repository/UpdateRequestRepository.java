package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UpdateRequestRepository extends JpaRepository<UpdateRequest, UUID>,
        JpaSpecificationExecutor<UpdateRequest> {

    /*
     * Languages already holding a request in this status for (employee, profile).
     * - A null profile is its own slot, the same way COALESCE works in uk_pending_key [QĐ-46].
     * - Used only to report skipped languages; the unique index is what enforces BR-19.
     */
    @Query("""
            select r.language from UpdateRequest r
            where r.employeeId = :employeeId
              and r.status = :status
              and ((:profileId is null and r.profileId is null) or r.profileId = :profileId)
            """)
    List<Language> findLanguagesByStatus(@Param("employeeId") UUID employeeId,
                                         @Param("profileId") UUID profileId,
                                         @Param("status") RequestStatus status);

    // ---------- Cancellation ----------

    List<UpdateRequest> findByCvIdAndStatusOrderByCreatedAtAsc(UUID cvId, RequestStatus status);

    List<UpdateRequest> findByEmployeeIdAndStatus(UUID employeeId, RequestStatus status);

    // Requests aimed at the profile itself or at any CV inside it
    @Query("""
            SELECT r FROM UpdateRequest r
            WHERE r.status = :status
              AND (r.profileId = :profileId
                   OR r.cvId IN (SELECT c.id FROM Cv c WHERE c.profileId = :profileId))
            """)
    List<UpdateRequest> findByProfileScopeAndStatus(@Param("profileId") UUID profileId,
                                                    @Param("status") RequestStatus status);

    // Compare-and-set on one row: 0 means someone else moved it first
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE UpdateRequest r
            SET r.status = :to, r.updatedBy = :actorId, r.updatedAt = :at
            WHERE r.id = :id AND r.status = :from
            """)
    int transitionById(@Param("id") UUID id,
                       @Param("from") RequestStatus from,
                       @Param("to") RequestStatus to,
                       @Param("actorId") UUID actorId,
                       @Param("at") LocalDateTime at);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE UpdateRequest r
            SET r.status = :to, r.updatedBy = :actorId, r.updatedAt = :at
            WHERE r.cvId = :cvId AND r.status = :from
            """)
    int transitionByCvId(@Param("cvId") UUID cvId,
                         @Param("from") RequestStatus from,
                         @Param("to") RequestStatus to,
                         @Param("actorId") UUID actorId,
                         @Param("at") LocalDateTime at);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE UpdateRequest r
            SET r.status = :to, r.updatedBy = :actorId, r.updatedAt = :at
            WHERE r.employeeId = :employeeId AND r.status = :from
            """)
    int transitionByEmployeeId(@Param("employeeId") UUID employeeId,
                               @Param("from") RequestStatus from,
                               @Param("to") RequestStatus to,
                               @Param("actorId") UUID actorId,
                               @Param("at") LocalDateTime at);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE UpdateRequest r
            SET r.status = :to, r.updatedBy = :actorId, r.updatedAt = :at
            WHERE r.status = :from
              AND (r.profileId = :profileId
                   OR r.cvId IN (SELECT c.id FROM Cv c WHERE c.profileId = :profileId))
            """)
    int transitionByProfileScope(@Param("profileId") UUID profileId,
                                 @Param("from") RequestStatus from,
                                 @Param("to") RequestStatus to,
                                 @Param("actorId") UUID actorId,
                                 @Param("at") LocalDateTime at);

    // ---------- Batch requests ----------

    // Preview/create: pending slots of these employees in one language
    List<UpdateRequest> findByEmployeeIdInAndLanguageAndStatus(Collection<UUID> employeeIds,
                                                               Language language,
                                                               RequestStatus status);

    // Worker, first run
    List<UpdateRequest> findByBatchRequestIdAndNotificationFailedFalse(UUID batchRequestId);

    // Resend: snapshot of the flagged children
    @Query("SELECT r.id FROM UpdateRequest r WHERE r.batchRequestId = :batchId AND r.notificationFailed = true")
    List<UUID> findFailedIdsByBatch(@Param("batchId") UUID batchId);

    // Failed-items page, ordered by employee name
    @Query(value = """
            SELECT r FROM UpdateRequest r, User u
            WHERE u.id = r.employeeId AND r.batchRequestId = :batchId AND r.notificationFailed = true
            ORDER BY u.fullName ASC, r.id ASC
            """,
            countQuery = """
            SELECT COUNT(r) FROM UpdateRequest r
            WHERE r.batchRequestId = :batchId AND r.notificationFailed = true
            """)
    Page<UpdateRequest> findFailedByBatch(@Param("batchId") UUID batchId, Pageable pageable);

    // CAS on the flag: 1 only when it really changed, so counters never double count
    @Modifying
    @Query("UPDATE UpdateRequest r SET r.notificationFailed = true WHERE r.id = :id AND r.notificationFailed = false")
    int markNotificationFailed(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE UpdateRequest r SET r.notificationFailed = false WHERE r.id = :id AND r.notificationFailed = true")
    int clearNotificationFailed(@Param("id") UUID id);
}

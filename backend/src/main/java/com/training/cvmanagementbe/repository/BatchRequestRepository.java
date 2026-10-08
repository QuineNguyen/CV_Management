package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.BatchRequest;
import com.training.cvmanagementbe.enums.cvs.BatchRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/*
 * Counters change in SQL, never read-modify-write in Java:
 * The worker and the email-failure callbacks can hit the same row concurrently.
 */
public interface BatchRequestRepository extends JpaRepository<BatchRequest, UUID> {

    // One child handled; processed never passes the total even on a redelivered message
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE BatchRequest b
            SET b.processedCount = CASE WHEN b.processedCount < b.totalCount
                                        THEN b.processedCount + 1 ELSE b.processedCount END,
                b.errorCount = b.errorCount + :errorDelta
            WHERE b.id = :id
            """)
    int addProgress(@Param("id") UUID id, @Param("errorDelta") int errorDelta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BatchRequest b SET b.errorCount = b.errorCount + :delta WHERE b.id = :id")
    int addErrors(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE BatchRequest b SET b.status = :target, b.processedCount = b.totalCount
            WHERE b.id = :id AND b.status = :current AND b.errorCount = 0
            """)
    int closeClean(@Param("id") UUID id,
                   @Param("current") BatchRequestStatus current,
                   @Param("target") BatchRequestStatus target);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE BatchRequest b SET b.status = :target, b.processedCount = b.totalCount
            WHERE b.id = :id AND b.status = :current AND b.errorCount > 0
            """)
    int closeWithErrors(@Param("id") UUID id,
                        @Param("current") BatchRequestStatus current,
                        @Param("target") BatchRequestStatus target);

    // CAS: only a finished batch reopens; a second click or a cancelled batch matches nothing
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE BatchRequest b
            SET b.status = :processing, b.processedCount = b.totalCount - :requeued
            WHERE b.id = :id AND b.status IN :finished
            """)
    int reopenForResend(@Param("id") UUID id,
                        @Param("requeued") int requeued,
                        @Param("processing") BatchRequestStatus processing,
                        @Param("finished") Collection<BatchRequestStatus> finished);

    Page<BatchRequest> findByStatus(BatchRequestStatus status, Pageable pageable);

    // Worker: a cancelled batch stops its loop
    @Query("SELECT b.status FROM BatchRequest b WHERE b.id = :id")
    Optional<BatchRequestStatus> findStatusById(@Param("id") UUID id);

    // CAS: a second, concurrent cancel matches nothing
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE BatchRequest b
            SET b.status = :cancelled, b.updatedBy = :actorId, b.updatedAt = :at
            WHERE b.id = :id AND b.status <> :cancelled
            """)
    int cancelBatch(@Param("id") UUID id,
                    @Param("cancelled") BatchRequestStatus cancelled,
                    @Param("actorId") UUID actorId,
                    @Param("at") LocalDateTime at);
}

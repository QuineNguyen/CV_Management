package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.CvDraft;
import com.training.cvmanagementbe.enums.DraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvDraftRepository extends JpaRepository<CvDraft, UUID> {

    // "At most one open draft" — PUBLISHED and CANCELLED do not count.
    Optional<CvDraft> findByCvIdAndStatusIn(UUID cvId, Collection<DraftStatus> openStatuses);

    boolean existsByCvIdAndStatusIn(UUID cvId, Collection<DraftStatus> statuses);

    List<CvDraft> findByCvIdInAndStatusIn(Collection<UUID> cvIds, Collection<DraftStatus> statuses);

    /**
     * Compare-and-set: moves a draft into the approval flow only if it is still in a status that
     * allows it. Zero rows affected means somebody acted first - a second tab, a double click, or
     * an Admin cancelling the draft mid-flight - and the caller must answer 409, never retry.
     *
     * - flushAutomatically pushes pending changes before the UPDATE so it sees them;
     * clearAutomatically drops the now-stale managed copy of the draft, which would
     * otherwise still report the old status to anything reading it later in the transaction.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CvDraft d
            SET d.status = :nextStatus,
                d.reviewRound = :reviewRound,
                d.submittedAt = :submittedAt
            WHERE d.id = :draftId AND d.status IN :expectedStatuses
            """)
    int markSubmitted(@Param("draftId") UUID draftId,
                      @Param("nextStatus") DraftStatus nextStatus,
                      @Param("reviewRound") int reviewRound,
                      @Param("submittedAt") LocalDateTime submittedAt,
                      @Param("expectedStatuses") Collection<DraftStatus> expectedStatuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CvDraft d
            SET d.status = :nextStatus, d.updatedBy = :actorId, d.updatedAt = :now
            WHERE d.id = :draftId AND d.status = :expectedStatus
    """)
    int transitionStatus(@Param("draftId") UUID draftId,
                         @Param("expectedStatus") DraftStatus expectedStatus,
                         @Param("nextStatus") DraftStatus nextStatus,
                         @Param("actorId") UUID actorId,
                         @Param("now") LocalDateTime now);

    // Guarded on the expected status so the reason never lands on a draft that moved on.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE CvDraft d SET d.lastRejectionReason = :reason
        WHERE d.id = :draftId AND d.status = :expectedStatus
        """)
    int updateLastRejectionReason(@Param("draftId") UUID draftId,
                                  @Param("reason") String reason,
                                  @Param("expectedStatus") DraftStatus expectedStatus);
}

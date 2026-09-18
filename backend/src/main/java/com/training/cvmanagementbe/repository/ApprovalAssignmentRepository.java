package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.ApprovalAssignment;
import com.training.cvmanagementbe.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalAssignmentRepository extends JpaRepository<ApprovalAssignment, UUID> {

    /**
     * Open-assignment count per candidate, for the "fewest open assignments" rule.
     *
     * - Returns only the candidates that actually have open work, so a person with none is absent
     * from the result rather than present with zero - the caller defaults a missing key to 0, which
     * is also what makes a never-assigned person win the tie-break outright.
     */
    @Query("""
            SELECT a.assigneeId, COUNT(a)
            FROM ApprovalAssignment a
            WHERE a.assigneeId IN :assigneeIds AND a.status = :status
            GROUP BY a.assigneeId
            """)
    List<Object[]> countByAssigneeIn(@Param("assigneeIds") Collection<UUID> assigneeIds,
                                     @Param("status") AssignmentStatus status);

    /**
     * Most recent assignment time per candidate, for the round-robin tie-break. Counts every row
     * regardless of status: "when was this person last given something" is about fairness of turn,
     * not about current load, so a closed assignment still marks their turn as taken.
     */
    @Query("""
            SELECT a.assigneeId, MAX(a.assignedAt)
            FROM ApprovalAssignment a
            WHERE a.assigneeId IN :assigneeIds
            GROUP BY a.assigneeId
            """)
    List<Object[]> lastAssignedAtByAssigneeIn(@Param("assigneeIds") Collection<UUID> assigneeIds);

    // The open assignment of a draft at one level.
    Optional<ApprovalAssignment> findByDraftIdAndLevelAndStatus(UUID draftId,
                                                                int level,
                                                                AssignmentStatus status);

    // The open assignment of a draft, whichever level it currently sits at.
    Optional<ApprovalAssignment> findByDraftIdAndStatus(UUID draftId, AssignmentStatus status);

    // The approval queue: only the caller's own open work.
    Page<ApprovalAssignment> findByAssigneeIdAndStatus(UUID assigneeId,
                                                       AssignmentStatus status,
                                                       Pageable pageable);

    long countByAssigneeIdAndStatus(UUID assigneeId, AssignmentStatus status);

    // History of a draft, newest first - shown beside the content when a reviewer opens it.
    List<ApprovalAssignment> findByDraftIdOrderByAssignedAtDesc(UUID draftId);

    /**
     * Closes an open row without loading it. Used when a level is completed, skipped or cancelled.
     * The status = ASSIGNED predicate in the WHERE clause makes this a compare-and-set:
     * zero rows affected means somebody else closed it first.
     */
    @Query("""
            UPDATE ApprovalAssignment a
            SET a.status = :nextStatus, a.closedAt = :closedAt
            WHERE a.id = :assignmentId AND a.status = :expectedStatus
            """)
    @org.springframework.data.jpa.repository.Modifying
    int closeIfOpen(@Param("assignmentId") UUID assignmentId,
                    @Param("expectedStatus") AssignmentStatus expectedStatus,
                    @Param("nextStatus") AssignmentStatus nextStatus,
                    @Param("closedAt") LocalDateTime closedAt);

    // Assignment half of the CAS: matches only while still assigned to this reviewer
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ApprovalAssignment a
            SET a.status = :nextStatus, a.closedAt = :closedAt
            WHERE a.id = :assignmentId
              AND a.assigneeId = :assigneeId
              AND a.status = :expectedStatus
    """)
    int closeForAssignee(@Param("assignmentId") UUID assignmentId,
                         @Param("assigneeId") UUID assigneeId,
                         @Param("expectedStatus") AssignmentStatus expectedStatus,
                         @Param("nextStatus") AssignmentStatus nextStatus,
                         @Param("closedAt") LocalDateTime closedAt);
}

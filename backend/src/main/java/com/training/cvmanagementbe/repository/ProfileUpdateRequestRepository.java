package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.ProfileUpdateRequest;
import com.training.cvmanagementbe.enums.profile_updates.ProfileUpdateStatus;
import com.training.cvmanagementbe.enums.users.Role;
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

public interface ProfileUpdateRequestRepository extends JpaRepository<ProfileUpdateRequest, UUID> {

    // Drives the My Profile status banner, whatever the outcome was.
    Optional<ProfileUpdateRequest> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    /*
     * Routing is data scope, not just an annotation: the same predicate has to appear in the list
     * query, the detail query and the state transition. These two carry it for the queue.
     */
    @Query("""
            SELECT r FROM ProfileUpdateRequest r
             WHERE (:status IS NULL OR r.status = :status)
               AND r.userId IN (SELECT u.id FROM User u WHERE u.role IN :requesterRoles)
            """)
    Page<ProfileUpdateRequest> searchInScope(@Param("status") ProfileUpdateStatus status,
                                             @Param("requesterRoles") Collection<Role> requesterRoles,
                                             Pageable pageable);

    @Query("""
            SELECT COUNT(r) FROM ProfileUpdateRequest r
             WHERE r.status = :status
               AND r.userId IN (SELECT u.id FROM User u WHERE u.role IN :requesterRoles)
            """)
    long countByStatusInScope(@Param("status") ProfileUpdateStatus status,
                              @Param("requesterRoles") Collection<Role> requesterRoles);

    /**
     * Closes a request only while it is still PENDING, and reports how many rows matched.
     *
     * <p>This is the whole concurrency control (BR-80). Unlike CV approval there is no exclusive
     * assignment in front of it - [QD-68] deliberately widened the set of people who can race, so
     * two reviewers pressing Approve and Reject at the same moment both read PENDING before
     * either commits. InnoDB serialises them on the row lock; the second re-evaluates the WHERE
     * clause against the now-committed state, matches nothing, and returns 0.
     *
     * <p>Also used by withdrawal, which is the same race from the requester's side.
     *
     * <p>clearAutomatically: a bulk update bypasses the persistence context, so without it the
     * entity in memory would still claim to be PENDING.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProfileUpdateRequest r
               SET r.status = :outcome,
                   r.rejectReason = :rejectReason,
                   r.reviewedBy = :reviewerId,
                   r.reviewedAt = :reviewedAt,
                   r.updatedBy = :actorId,
                   r.updatedAt = :now
             WHERE r.id = :id
               AND r.status = com.training.cvmanagementbe.enums.ProfileUpdateStatus.PENDING
            """)
    int closeIfPending(@Param("id") UUID id,
                       @Param("outcome") ProfileUpdateStatus outcome,
                       @Param("rejectReason") String rejectReason,
                       @Param("reviewerId") UUID reviewerId,
                       @Param("reviewedAt") LocalDateTime reviewedAt,
                       @Param("actorId") UUID actorId,
                       @Param("now") LocalDateTime now);
}

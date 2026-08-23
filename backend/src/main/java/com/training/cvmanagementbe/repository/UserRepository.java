package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.AccountStatus;
import com.training.cvmanagementbe.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByPrimaryDepartmentId(UUID departmentId);

    // Full-scope search used by ADMIN and HR.
    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(u.email) LIKE :keyword
                   OR LOWER(u.username) LIKE :keyword)
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:departmentId IS NULL OR u.primaryDepartmentId = :departmentId)
            """)
    Page<User> search(@Param("keyword") String keyword,
                      @Param("role") Role role,
                      @Param("status") AccountStatus status,
                      @Param("departmentId") UUID departmentId,
                      Pageable pageable);

    // Scoped search used by TECH_LEAD: only members of the teams they lead.
    @Query("""
            SELECT u FROM User u
            WHERE u.id IN (SELECT tm.userId FROM TeamMember tm WHERE tm.teamId IN :teamIds)
              AND (:keyword IS NULL
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(u.email) LIKE :keyword
                   OR LOWER(u.username) LIKE :keyword)
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:departmentId IS NULL OR u.primaryDepartmentId = :departmentId)
            """)
    Page<User> searchByTeamIds(@Param("keyword") String keyword,
                               @Param("teamIds") Collection<UUID> teamIds,
                               @Param("role") Role role,
                               @Param("status") AccountStatus status,
                               @Param("departmentId") UUID departmentId,
                               Pageable pageable);

    List<User> findByRoleAndStatusOrderByFullNameAsc(Role role, AccountStatus status);

    long countByRoleAndStatus(Role role, AccountStatus status);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findByIdIn(Collection<UUID> ids);

    // Ids of active users belonging to any of the given teams, used by AuthScope checks.
    @Query("SELECT DISTINCT tm.userId FROM TeamMember tm WHERE tm.teamId IN :teamIds")
    List<UUID> findUserIdsByTeamIds(@Param("teamIds") Collection<UUID> teamIds);

    // Cancels every open draft owned by the user.
    @Modifying
    @Query(value = "UPDATE cv_drafts SET status = :cancelled, updated_at = CURRENT_TIMESTAMP "
            + "WHERE owner_id = :userId AND status IN (:openStatuses)",
            nativeQuery = true)
    int cancelOpenDraftsByOwner(@Param("userId") UUID userId,
                                @Param("openStatuses") Collection<String> openStatuses,
                                @Param("cancelled") String cancelled);

    // Cancels every pending update request addressed to the user.
    @Modifying
    @Query(value = "UPDATE update_requests SET status = :cancelled, updated_at = CURRENT_TIMESTAMP "
            + "WHERE employee_id = :userId AND status = :pending",
            nativeQuery = true)
    int cancelPendingRequestsByEmployee(@Param("userId") UUID userId,
                                        @Param("pending") String pending,
                                        @Param("cancelled") String cancelled);

    // Clears approval assignments still waiting on the user.
    @Modifying
    @Query(value = "UPDATE approval_assignments SET status = :cancelled, updated_at = CURRENT_TIMESTAMP "
            + "WHERE assignee_id = :userId AND status = :assigned",
            nativeQuery = true)
    int cancelAssignmentsByAssignee(@Param("userId") UUID userId,
                                    @Param("assigned") String assigned,
                                    @Param("cancelled") String cancelled);

    // Narrowed search used by EMPLOYEE, who may only see the own record.
    @Query("""
            SELECT u FROM User u
            WHERE u.id IN :ids
              AND (:keyword IS NULL
                   OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(u.email) LIKE :keyword
                   OR LOWER(u.username) LIKE :keyword)
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:departmentId IS NULL OR u.primaryDepartmentId = :departmentId)
            """)
    Page<User> searchByIds(@Param("keyword") String keyword,
                           @Param("ids") Collection<UUID> ids,
                           @Param("role") Role role,
                           @Param("status") AccountStatus status,
                           @Param("departmentId") UUID departmentId,
                           Pageable pageable);
}

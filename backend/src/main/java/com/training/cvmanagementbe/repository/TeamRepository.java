package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    // Keyword is expected lower-cased and already wrapped with % by the service.
    @Query("""
            SELECT t FROM Team t
            WHERE (:keyword IS NULL
                   OR LOWER(t.code) LIKE :keyword
                   OR LOWER(t.name) LIKE :keyword)
            """)
    Page<Team> search(@Param("keyword") String keyword, Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    boolean existsByTechLeadId(UUID techLeadId);

    List<Team> findByTechLeadId(UUID techLeadId);

    List<Team> findByIdIn(Collection<UUID> ids);

    // Blocks team deletion when an active CV profile still points to it.
    @Query(value = "SELECT COUNT(1) FROM cv_profiles p "
            + "WHERE p.linked_team_id = :teamId AND p.lifecycle_status = 'ACTIVE'",
            nativeQuery = true)
    long countActiveProfilesByTeamId(@Param("teamId") UUID teamId);

    // Active profiles of one user linked to one team, checked before removing a member.
    @Query(value = "SELECT COUNT(1) FROM cv_profiles p "
            + "WHERE p.linked_team_id = :teamId AND p.employee_id = :userId "
            + "AND p.lifecycle_status = 'ACTIVE'",
            nativeQuery = true)
    long countActiveProfilesByTeamIdAndUserId(@Param("teamId") UUID teamId,
                                              @Param("userId") UUID userId);

    // Highest display order across the whole catalogue, used to append new teams (step 10).
    // The scope is the whole table, not a department: teams do not belong to one [QD-66].
    @Query("SELECT COALESCE(MAX(t.displayOrder), 0) FROM Team t")
    int findMaxDisplayOrder();
}

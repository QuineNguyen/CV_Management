package com.training.cvmanagementbe.repository;

import com.training.cvmanagementbe.entity.models.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    List<TeamMember> findByTeamId(UUID teamId);

    List<TeamMember> findByUserId(UUID userId);

    boolean existsByTeamId(UUID teamId);

    boolean existsByUserIdAndTeamId(UUID userId, UUID teamId);

    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    Optional<TeamMember> findByUserIdAndPrimaryTeamTrue(UUID userId);

    long countByTeamId(UUID teamId);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    void deleteByTeamIdAndUserId(UUID teamId, UUID userId);

    // Team ids the given user belongs to.
    @Query("SELECT tm.teamId FROM TeamMember tm WHERE tm.userId = :userId")
    List<UUID> findTeamIdsByUserId(@Param("userId") UUID userId);

    // Member rows of many teams at once, used to build listing responses.
    List<TeamMember> findByTeamIdIn(Collection<UUID> teamIds);

    // Member rows of many users at once, avoids N+1 on the user listing.
    List<TeamMember> findByUserIdIn(Collection<UUID> userIds);

    // Member count per team, returned as [teamId, count] rows.
    @Query("SELECT tm.teamId, COUNT(tm) FROM TeamMember tm "
            + "WHERE tm.teamId IN :teamIds GROUP BY tm.teamId")
    List<Object[]> countGroupedByTeamIds(@Param("teamIds") Collection<UUID> teamIds);
}

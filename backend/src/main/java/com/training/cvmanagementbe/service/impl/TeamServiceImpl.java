package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.TeamRequest;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.dto.response.TeamMemberResponse;
import com.training.cvmanagementbe.dto.response.TeamResponse;
import com.training.cvmanagementbe.entity.models.Team;
import com.training.cvmanagementbe.entity.models.TeamMember;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.*;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.TeamMemberRepository;
import com.training.cvmanagementbe.repository.TeamRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamServiceImpl implements TeamService {

    private static final int ORDER_STEP = 10;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    @Override
    public PagedResponse<TeamResponse> search(String keyword, Pageable pageable) {
        String pattern = (keyword == null || keyword.isBlank())
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        Page<Team> page = teamRepository.search(pattern, pageable);
        return PagedResponse.of(page, toResponses(page.getContent()));
    }

    @Override
    public TeamResponse getById(UUID id) {
        return toResponses(List.of(requireTeam(id))).get(0);
    }

    @Override
    public List<TeamMemberResponse> getMembers(UUID teamId) {
        requireTeam(teamId);

        List<TeamMember> memberships = teamMemberRepository.findByTeamId(teamId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> userById = findUsers(
                memberships.stream().map(TeamMember::getUserId).collect(Collectors.toSet())
        );

        // Primary members first, then alphabetically by full name
        return memberships.stream()
                .map(membership -> toMemberResponse(membership, userById.get(membership.getUserId())))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TeamMemberResponse::primaryTeam).reversed()
                        .thenComparing(TeamMemberResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ---------- Commands ----------
    @Override
    @Transactional
    public TeamResponse create(TeamRequest request) {
        validateCodeAvailable(request.code(), null);
        requireValidTechLead(request.techLeadId());

        Team team = new Team();
        applyRequest(team, request);
        team.setDisplayOrder(request.displayOrder() != null
                ? request.displayOrder()
                : nextDisplayOrder());

        Team saved = teamRepository.save(team);
        auditLogger.record(Action.CREATE_TEAM, TargetType.TEAM,
                saved.getId(), null, toFlatResponse(saved));
        return toResponses(List.of(saved)).get(0);
    }

    @Override
    @Transactional
    public TeamResponse update(UUID id, TeamRequest request) {
        Team team = requireTeam(id);

        validateCodeAvailable(request.code(), id);
        requireValidTechLead(request.techLeadId());

        // Snapshot before mutating so the audit entry keeps both sides
        TeamResponse before = toFlatResponse(team);
        applyRequest(team, request);

        // One flat catalogue, so an omitted order simply keeps the current one
        if (request.displayOrder() != null) {
            team.setDisplayOrder(request.displayOrder());
        }

        Team saved = teamRepository.save(team);
        auditLogger.record(Action.UPDATE_TEAM, TargetType.TEAM,
                saved.getId(), before, toFlatResponse(saved));
        return toResponses(List.of(saved)).get(0);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Team team = requireTeam(id);
        validateDeletable(id);

        TeamResponse before = toFlatResponse(team);
        teamRepository.delete(team);
        auditLogger.record(Action.DELETE_TEAM, TargetType.TEAM, id, before, null);
    }

    @Override
    @Transactional
    public void addMember(UUID teamId, UUID userId) {
        requireTeam(teamId);
        User user = requireUser(userId);
        validateAddable(teamId, user);

        // A user must always have exactly one primary team, so the first one wins it
        TeamMember membership = TeamMember.builder()
                .userId(userId)
                .teamId(teamId)
                .primaryTeam(teamMemberRepository.countByTeamId(userId) == 0)
                .build();

        TeamMember saved = teamMemberRepository.save(membership);
        auditLogger.record(Action.UPDATE_TEAM, TargetType.TEAM,
                teamId, null, toMemberResponse(saved, user));
    }

    @Override
    @Transactional
    public void removeMember(UUID teamId, UUID userId) {
        requireTeam(teamId);
        User user = requireUser(userId);

        TeamMember membership = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ApiException.NotFoundException("team member", userId));
        validateRemovable(teamId, userId,membership);

        TeamMemberResponse before = toMemberResponse(membership, user);
        teamMemberRepository.delete(membership);
        auditLogger.record(Action.UPDATE_TEAM, TargetType.TEAM, teamId, before, null);
    }

    // ---------- Validation ----------

    private void validateCodeAvailable(String code, UUID selfId) {
        String normalized = normalizeCode(code);
        boolean taken = selfId == null
                ? teamRepository.existsByCode(normalized)
                : teamRepository.existsByCodeAndIdNot(normalized, selfId);
        if (taken) {
            throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_TEAM_CODE);
        }
    }

    // Tech lead must exist, hold the TECH_LEAD role and be ACTIVE
    private void requireValidTechLead(UUID techLeadId) {
        User lead = requireUser(techLeadId);
        if (lead.getRole() != Role.TECH_LEAD) {
            throw new ApiException.BusinessRuleException(ErrorCode.INVALID_TECH_LEAD_ROLE);
        }
        if (!lead.isActive()) {
            throw new ApiException.BusinessRuleException(ErrorCode.INVALID_TECH_LEAD_INACTIVE);
        }
    }

    // Only an empty team can be deleted: no member, no active CV profile linked
    private void validateDeletable(UUID id) {
        if (teamMemberRepository.existsByTeamId(id)) {
            throw new ApiException.BusinessRuleException(ErrorCode.TEAM_HAS_MEMBERS);
        }
        if (teamRepository.countActiveProfilesByTeamId(id) > 0) {
            throw new ApiException.BusinessRuleException(ErrorCode.TEAM_HAS_PROFILES);
        }
    }

    private void validateAddable(UUID teamId, User user) {
        if (!user.isActive()) {
            throw new ApiException.BusinessRuleException(ErrorCode.INVALID_TECH_LEAD_INACTIVE);
        }
        if (teamMemberRepository.existsByUserIdAndTeamId(user.getId(), teamId)) {
            throw new ApiException.BusinessRuleException(ErrorCode.USER_ALREADY_IN_TEAM);
        }
    }

    private void validateRemovable(UUID teamId, UUID userId, TeamMember membership) {
        // Removing the last membership would leave the user without any team
        if (teamMemberRepository.countByTeamId(userId) <= 1) {
            throw new ApiException.BusinessRuleException(ErrorCode.CANNOT_REMOVE_ONLY_TEAM);
        }
        // Primary team is reassigned through the user form, not here
        if (membership.isPrimaryTeam()) {
            throw new ApiException.BusinessRuleException(ErrorCode.CANNOT_REMOVE_PRIMARY_TEAM);
        }
        // An active CV profile of this user still points at this team
        if (teamRepository.countActiveProfilesByTeamIdAndUserId(teamId, userId) > 0) {
            throw new ApiException.BusinessRuleException(ErrorCode.TEAM_HAS_PROFILES);
        }
    }

    // ---------- Private helpers ----------
    // Tech lead names and member counts are resolved in bulk to avoid N + 1
    private List<TeamResponse> toResponses(List<Team> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> techLeadById = findUsers(
                teams.stream().map(Team::getTechLeadId).collect(Collectors.toSet())
        );
        Map<UUID, Long> memberCountByTeamId = countMembers(
                teams.stream().map(Team::getId).collect(Collectors.toSet())
        );

        return teams.stream()
                .map(team -> toResponse(team,
                        techLeadById.get(team.getTechLeadId()),
                        memberCountByTeamId.getOrDefault(team.getId(), 0L)))
                .toList();
    }

    private Map<UUID, User> findUsers(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<UUID, Long> countMembers(Collection<UUID> teamIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : teamMemberRepository.countGroupedByTeamIds(teamIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private int nextDisplayOrder() {
        return teamRepository.findMaxDisplayOrder() + ORDER_STEP;
    }

    private void applyRequest(Team target, TeamRequest request) {
        target.setCode(normalizeCode(request.code()));
        target.setName(request.name().trim());
        target.setDescription(trimToNull(request.description()));
        target.setTechLeadId(request.techLeadId());
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Team requireTeam(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFoundException("team", id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFoundException("user", id));
    }

    // Audit snapshot: joined names are not needed and would cost extra queries
    private TeamResponse toFlatResponse(Team team) {
        return toResponse(team, null, 0L);
    }

    private TeamResponse toResponse(Team team, User techLead, long memberCount) {
        return new TeamResponse(
                team.getId(),
                team.getCode(),
                team.getName(),
                team.getDescription(),
                team.getTechLeadId(),
                techLead == null ? null : techLead.getFullName(),
                team.getDisplayOrder(),
                memberCount
        );
    }

    private TeamMemberResponse toMemberResponse(TeamMember membership, User user) {
        if (user == null) {
            return null;
        }
        return new TeamMemberResponse(
                membership.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                membership.isPrimaryTeam()
        );
    }
}

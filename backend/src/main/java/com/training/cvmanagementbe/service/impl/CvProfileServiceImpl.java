package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.CvProfileRequest;
import com.training.cvmanagementbe.dto.response.CvProfileResponse;
import com.training.cvmanagementbe.dto.response.EmployeeTeamResponse;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.CvProfile;
import com.training.cvmanagementbe.entity.models.Team;
import com.training.cvmanagementbe.entity.models.TeamMember;
import com.training.cvmanagementbe.enums.*;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.CvProfileRepository;
import com.training.cvmanagementbe.repository.TeamMemberRepository;
import com.training.cvmanagementbe.repository.TeamRepository;
import com.training.cvmanagementbe.service.CvProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CvProfileServiceImpl implements CvProfileService {

    // Name of the profile created by ensureProfileExists, before the employee renames it.
    private static final String DEFAULT_PROFILE_NAME = "Primary profile";

    private final CvProfileRepository cvProfileRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditLogger auditLogger;

    // ---------- Queries ----------

    @Override
    public PagedResponse<CvProfileResponse> listByEmployee(UUID employeeId, Pageable pageable) {
        requireCanRead(employeeId);

        Page<CvProfile> page = cvProfileRepository
                .findByEmployeeIdAndLifecycleStatus(employeeId, LifecycleStatus.ACTIVE, pageable);

        Map<UUID, Team> teams = loadTeams(page.getContent());
        List<CvProfileResponse> content = page.getContent().stream()
                .map(profile -> toResponse(profile, teams.get(profile.getLinkedTeamId())))
                .toList();

        return PagedResponse.of(page, content);
    }

    @Override
    public List<EmployeeTeamResponse> listAssignableTeams(UUID employeeId) {
        requireCanRead(employeeId);

        List<TeamMember> memberships = teamMemberRepository.findByUserId(employeeId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<UUID, Boolean> primaryByTeamId = memberships.stream().collect(Collectors.toMap(
                TeamMember::getTeamId, TeamMember::isPrimaryTeam, (left, right) -> left
        ));

        // Primary team first, then the catalogue order used everywhere else: (displayOrder, name).
        return teamRepository.findAllById(primaryByTeamId.keySet()).stream()
                .sorted(Comparator.comparing((Team team) -> !primaryByTeamId.get(team.getId()))
                        .thenComparingInt(Team::getDisplayOrder)
                        .thenComparing(Team::getName))
                .map(team -> new EmployeeTeamResponse(
                        team.getId(), team.getCode(), team.getName(), primaryByTeamId.get(team.getId())
                ))
                .toList();
    }

    @Override
    public CvProfileResponse getById(UUID id) {
        CvProfile profile = requireActiveProfile(id);
        requireCanRead(profile.getEmployeeId());
        return toResponse(profile, teamRepository.findById(profile.getLinkedTeamId()).orElse(null));
    }

    // ---------- Commands ----------

    @Override
    @Transactional
    public CvProfileResponse create(UUID employeeId, CvProfileRequest request) {
        requireCanManage(employeeId);

        String name = request.name().trim();
        validateNameAvailable(employeeId, name, null);
        validateTeamMembership(employeeId, request.linkedTeamId());

        CvProfile profile = new CvProfile();
        profile.setEmployeeId(employeeId);
        applyRequest(profile, request, name);

        // The first active profile of an employee is the primary one by definition.
        profile.setPrimary(!cvProfileRepository
                .existsByEmployeeIdAndLifecycleStatus(employeeId, LifecycleStatus.ACTIVE));

        CvProfile saved = cvProfileRepository.save(profile);
        CvProfileResponse response = toResponse(saved, teamRepository.findById(saved.getLinkedTeamId()).orElse(null));

        auditLogger.record(Action.CREATE_CV_PROFILE, TargetType.CV_PROFILE, saved.getId(), null, response);
        return response;
    }

    @Override
    @Transactional
    public CvProfileResponse update(UUID id, CvProfileRequest request) {
        CvProfile profile = requireActiveProfile(id);
        requireCanManage(profile.getEmployeeId());

        String name = request.name().trim();
        validateNameAvailable(profile.getEmployeeId(), name, id);
        validateTeamMembership(profile.getEmployeeId(), request.linkedTeamId());

        Team beforeTeam = teamRepository.findById(profile.getLinkedTeamId()).orElse(null);
        CvProfileResponse before = toResponse(profile, beforeTeam);

        applyRequest(profile, request, name);
        CvProfile saved = cvProfileRepository.save(profile);

        CvProfileResponse after = toResponse(saved, teamRepository.findById(saved.getLinkedTeamId()).orElse(null));
        auditLogger.record(Action.UPDATE_CV_PROFILE, TargetType.CV_PROFILE, saved.getId(), before, after);
        return after;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CvProfile profile = requireActiveProfile(id);
        requireCanManage(profile.getEmployeeId());
        validateDeletable(profile);

        CvProfileResponse before = toResponse(profile, teamRepository.findById(profile.getLinkedTeamId()).orElse(null));

        UUID actorId = CurrentActor.requireUserId();
        LocalDateTime deletedAt = LocalDateTime.now();

        profile.setLifecycleStatus(LifecycleStatus.DELETED);
        profile.setDeletedBy(actorId);
        profile.setDeletedAt(deletedAt);
        cvProfileRepository.save(profile);

        // Same transaction: a profile and its CVs must never disagree on being deleted.
        cvProfileRepository.softDeleteCvsByProfileId(id, actorId, deletedAt);

        auditLogger.record(Action.DELETE_CV_PROFILE, TargetType.CV_PROFILE, id, before, null);
    }

    @Override
    @Transactional
    public CvProfileResponse setPrimary(UUID id) {
        CvProfile target = requireActiveProfile(id);
        requireCanManage(target.getEmployeeId());

        Team team = teamRepository.findById(target.getLinkedTeamId()).orElse(null);
        if (target.isPrimary()) {
            return toResponse(target, team);
        }

        CvProfileResponse before = toResponse(target, team);
        demoteCurrentPrimary(target.getEmployeeId(), id);

        target.setPrimary(true);
        CvProfile saved = cvProfileRepository.save(target);

        CvProfileResponse after = toResponse(saved, team);
        auditLogger.record(Action.SET_PRIMARY_CV_PROFILE, TargetType.CV_PROFILE, id, before, after);
        return after;
    }

    @Override
    @Transactional
    public CvProfileResponse ensureProfileExists(UUID employeeId) {
        requireCanManage(employeeId);

        List<CvProfile> existing = cvProfileRepository
                .findByEmployeeIdAndLifecycleStatus(employeeId, LifecycleStatus.ACTIVE);

        if (!existing.isEmpty()) {
            return toResponseWithTeam(resolvePrimary(existing));
        }

        // First profile inherits the employee's primary team, which is what makes it reviewable.
        TeamMember primaryTeam = teamMemberRepository.findByUserIdAndPrimaryTeamTrue(employeeId)
                .orElseThrow(() -> new ApiException.BusinessRuleException(ErrorCode.PRIMARY_TEAM_REQUIRED));

        CvProfile profile = new CvProfile();
        profile.setEmployeeId(employeeId);
        profile.setName(DEFAULT_PROFILE_NAME);
        profile.setLinkedTeamId(primaryTeam.getTeamId());
        profile.setPrimary(true);
        profile.setLifecycleStatus(LifecycleStatus.ACTIVE);

        CvProfile saved = cvProfileRepository.save(profile);
        CvProfileResponse response = toResponseWithTeam(saved);

        auditLogger.record(Action.CREATE_CV_PROFILE, TargetType.CV_PROFILE, saved.getId(), null, response);
        return response;
    }

    // ---------- Validation ----------

    private void validateNameAvailable(UUID employeeId, String name, UUID excludeId) {
        boolean taken = excludeId == null
                ? cvProfileRepository.existsByEmployeeIdAndNameAndLifecycleStatus(
                        employeeId, name, LifecycleStatus.ACTIVE)
                : cvProfileRepository.existsByEmployeeIdAndNameAndLifecycleStatusAndIdNot(
                        employeeId, name, LifecycleStatus.ACTIVE, excludeId);

        if (taken) {
            throw new ApiException.BusinessRuleException(ErrorCode.PROFILE_NAME_TAKEN);
        }
    }

    // The linked team decides the level-1 reviewer, so it must be a team the employee is in
    private void validateTeamMembership(UUID employeeId, UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new ApiException.NotFoundException("team", teamId);
        }
        if (!teamMemberRepository.existsByUserIdAndTeamId(employeeId, teamId)) {
            throw new ApiException.BusinessRuleException(ErrorCode.LINKED_TEAM_NOT_MEMBER);
        }
    }

    private void validateDeletable(CvProfile profile) {
        if (profile.isPrimary()) {
            throw new ApiException.BusinessRuleException(ErrorCode.CANNOT_DELETE_PRIMARY_PROFILE);
        }
        if (cvProfileRepository.countPendingDraftsByProfileId(profile.getId()) > 0) {
            throw new ApiException.BusinessRuleException(ErrorCode.PROFILE_HAS_PENDING_DRAFTS);
        }
    }

    // ---------- Access control ----------
    // Presentation hides what it can; these two are the enforcement point.

    private void requireCanRead(UUID employeeId) {
        Role role = CurrentActor.requireRole();
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        requireSelf(employeeId);
    }

    private void requireCanManage(UUID employeeId) {
        if (CurrentActor.requireRole() == Role.ADMIN) {
            return;
        }
        requireSelf(employeeId);
    }

    private void requireSelf(UUID employeeId) {
        if (!CurrentActor.requireUserId().equals(employeeId)) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
    }

    // ---------- Private helpers ----------

    private void demoteCurrentPrimary(UUID employeeId, UUID excludeId) {
        cvProfileRepository
                .findByEmployeeIdAndPrimaryTrueAndLifecycleStatus(employeeId, LifecycleStatus.ACTIVE)
                .filter(current -> !current.getId().equals(excludeId))
                .ifPresent(current -> {
                    current.setPrimary(false);
                    // Flushed before the promotion below, so the unique index never sees two primaries.
                    cvProfileRepository.saveAndFlush(current);
                });
    }

    // Falls back to promoting the oldest profile if the primary flag was lost by a partial write.
    private CvProfile resolvePrimary(List<CvProfile> profiles) {
        Optional<CvProfile> primary = profiles.stream().filter(CvProfile::isPrimary).findFirst();
        if (primary.isPresent()) {
            return primary.get();
        }

        CvProfile promoted = profiles.stream()
                .min(Comparator.comparing(CvProfile::getDeletedAt))
                .orElseThrow(() -> new IllegalStateException("Profile list was checked to be non-empty"));
        promoted.setPrimary(true);
        return cvProfileRepository.save(promoted);
    }

    private void applyRequest(CvProfile target, CvProfileRequest request, String normalisedName) {
        target.setName(normalisedName);
        target.setDescription(blankToNull(request.description()));
        target.setLinkedTeamId(request.linkedTeamId());
    }

    // One query per page instead of one per row.
    private Map<UUID, Team> loadTeams(List<CvProfile> profiles) {
        Set<UUID> teamIds = profiles.stream().map(CvProfile::getLinkedTeamId).collect(Collectors.toSet());
        return teamIds.isEmpty()
                ? Map.of()
                : teamRepository.findAllById(teamIds).stream()
                        .collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    private CvProfileResponse toResponseWithTeam(CvProfile profile) {
        return toResponse(profile, teamRepository.findById(profile.getLinkedTeamId()).orElse(null));
    }

    private CvProfileResponse toResponse(CvProfile profile, Team team) {
        return new CvProfileResponse(
                profile.getId(),
                profile.getEmployeeId(),
                profile.getName(),
                profile.getDescription(),
                profile.isPrimary(),
                profile.getLinkedTeamId(),
                team == null ? null : team.getCode(),
                team == null ? null : team.getName(),
                // Counted per row: a page holds at most a few dozen profiles at this scale.
                cvProfileRepository.countActiveCvsByProfileId(profile.getId()),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private CvProfile requireActiveProfile(UUID id) {
        return cvProfileRepository.findByIdAndLifecycleStatus(id, LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", id));
    }
}

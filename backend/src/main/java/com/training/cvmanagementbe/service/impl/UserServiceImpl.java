package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.config.auth.PasswordGenerator;
import com.training.cvmanagementbe.dto.request.*;
import com.training.cvmanagementbe.dto.response.*;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.*;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.DepartmentRepository;
import com.training.cvmanagementbe.repository.TeamMemberRepository;
import com.training.cvmanagementbe.repository.TeamRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    // The system must always keep at least one active admin
    private static final long MIN_ACTIVE_ADMINS = 1L;

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final JwtService jwtService;
    private final AuditLogger auditLogger;

    @Override
    public PagedResponse<UserResponse> search(String keyword,
                                              Role role,
                                              AccountStatus status,
                                              UUID departmentId,
                                              Pageable pageable) {
        String pattern = (keyword == null || keyword.isBlank())
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        Page<User> page = queryInScope(pattern, role, status, departmentId, pageable);
        return PagedResponse.of(page, toResponses(page.getContent()));
    }

    @Override
    public UserResponse getById(UUID id) {
        User user = requireUser(id);
        validateInScope(id);

        // Led team drive the deactivate dialog, so they are loaded on detail only
        UserResponse response = toResponses(List.of(user)).get(0);
        return withLedTeams(response, teamRepository.findByTechLeadId(id));
    }

    @Override
    public List<TechLeadOption> getActiveTechLeads() {
        return userRepository.findByRoleAndStatusOrderByFullNameAsc(Role.TECH_LEAD, AccountStatus.ACTIVE)
                .stream()
                .map(lead -> new TechLeadOption(
                        lead.getId(), lead.getFullName(), lead.getEmail(), lead.getUsername()))
                .toList();
    }

    // ---------- Commands ----------

    @Override
    @Transactional
    public CreatedUserResponse create(CreateUserRequest request) {
        validateIdentityAvailable(request.email(), request.username());
        requireDepartmentExists(request.primaryDepartmentId());
        validateTeamAssignments(request.teams());

        // Handed to the admin once; the account starts in must-change-password state
        String temporaryPassword = passwordGenerator.generate();

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizeEmail(request.email()));
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setRole(request.role());
        user.setPrimaryDepartmentId(request.primaryDepartmentId());
        user.setStatus(AccountStatus.ACTIVE);
        user.setDateOfBirth(request.dateOfBirth());
        user.setPhoneNumber(trimToNull(request.phoneNumber()));
        user.setAddress(trimToNull(request.address()));
        user.setMustChangePassword(true);
        user.setFailedLoginCount(0);
        user.setTokenValidFrom(jwtService.revocationMark());

        User saved = userRepository.save(user);
        replaceMemberships(saved.getId(), request.teams());

        // Audit records the event only - never the password itself
        auditLogger.record(Action.CREATE_USER, TargetType.USER,
                saved.getId(), null, toFlatResponse(saved));

        return new CreatedUserResponse(getDetail(saved.getId()), temporaryPassword);
    }

    @Override
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = requireUser(id);
        requireDepartmentExists(request.primaryDepartmentId());
        validateTeamAssignments(request.teams());
        validateMembershipsRemovable(id, request.teams());

        UserResponse before = toFlatResponse(user);
        boolean roleChanged = user.getRole() != request.role();
        if (roleChanged) {
            validateRoleChange(user, request.role());
        }

        // Email and username are immutable, so they are absent from the request
        user.setUsername(request.fullName().trim());
        user.setRole(request.role());
        user.setPrimaryDepartmentId(request.primaryDepartmentId());
        user.setDateOfBirth(request.dateOfbirth());
        user.setPhoneNumber(trimToNull(request.phoneNumber()));
        user.setAddress(trimToNull(request.address()));

        // The role lives inside the JWT payload, so changing it must revoke old tokens
        if (roleChanged) {
            user.setTokenValidFrom(jwtService.revocationMark());
        }

        User saved = userRepository.save(user);
        replaceMemberships(id, request.teams());

        if (roleChanged) {
            auditLogger.record(Action.CHANGE_ROLE, TargetType.USER,
                    id, before.role(), saved.getRole());
        }
        auditLogger.record(Action.UPDATE_USER, TargetType.USER,
                id, before, toFlatResponse(saved));

        return getDetail(id);
    }

    @Override
    @Transactional
    public void deactivate(UUID id, DeactivateUserRequest request) {
        User user = requireUser(id);
        validateDeactivatable(user);

        UserResponse before = toFlatResponse(user);
        List<Team> ledTeams = teamRepository.findByTechLeadId(id);
        handOverLedTeams(id, ledTeams, request);

        // Everything below runs in the same transaction as the status flip
        userRepository.cancelOpenDraftsByOwner(id,
                DraftStatus.openStatusNames(), DraftStatus.CANCELLED.name());
        userRepository.cancelPendingRequestsByEmployee(id,
                RequestStatus.PENDING.name(), RequestStatus.CANCELLED.name());
        userRepository.cancelAssignmentsByAssignee(id,
                AssignmentStatus.ASSIGNED.name(), AssignmentStatus.CANCELLED.name());

        user.setStatus(AccountStatus.INACTIVE);
        user.setTokenValidFrom(jwtService.revocationMark());

        User saved = userRepository.save(user);
        auditLogger.record(Action.DEACTIVATE_USER, TargetType.USER,
                id, before, toFlatResponse(saved));
    }

    @Override
    @Transactional
    public void activate(UUID id) {
        User user = requireUser(id);
        if (user.isActive()) {
            throw new ApiException.BusinessRuleException(ErrorCode.USER_ALREADY_ACTIVE);
        }

        UserResponse before = toFlatResponse(user);
        user.setStatus(AccountStatus.ACTIVE);

        User saved = userRepository.save(user);
        auditLogger.record(Action.UPDATE_USER, TargetType.USER,
                id, before, toFlatResponse(saved));
    }

    // ---------- Validation ----------

    private void validateIdentityAvailable(String email, String username) {
        if (userRepository.existsByEmail(normalizeEmail(email))) {
            throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByUsername(username.trim())) {
            throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_USERNAME);
        }
    }

    // At least one team, exactly one primary, no duplicates, every team must exist
    private void validateTeamAssignments(List<TeamAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            throw new ApiException.BusinessRuleException(ErrorCode.TEAM_MEMBERSHIP_REQUIRED);
        }

        Set<UUID> teamIds = assignments.stream().map(TeamAssignment::teamId).collect(Collectors.toSet());
        if (teamIds.size() != assignments.size()) {
            throw new ApiException.BusinessRuleException(ErrorCode.DUPLICATE_TEAM_ASSIGNMENT);
        }

        long primaryCount = assignments.stream().filter(TeamAssignment::primary).count();
        if (primaryCount != 1) {
            throw new ApiException.BusinessRuleException(ErrorCode.PRIMARY_TEAM_REQUIRED);
        }

        Set<UUID> existingIds = teamRepository.findByIdIn(teamIds).stream()
                .map(Team::getId).collect(Collectors.toSet());
        for (UUID teamId : teamIds) {
            if (!existingIds.contains(teamId)) {
                throw new ApiException.NotFoundException("team", teamId);
            }
        }
    }

    // A team can only be dropped when no active CV profile of this user points at it
    private void validateMembershipsRemovable(UUID userId, List<TeamAssignment> assignments) {
        Set<UUID> keptTeamIds = assignments.stream()
                .map(TeamAssignment::teamId).collect(Collectors.toSet());

        for (UUID currentTeamId : teamMemberRepository.findTeamIdsByUserId(userId)) {
            if (keptTeamIds.contains(currentTeamId)) {
                continue;
            }
            if (teamRepository.countActiveProfilesByTeamIdAndUserId(currentTeamId, userId) > 0) {
                throw new ApiException.BusinessRuleException(ErrorCode.TEAM_HAS_PROFILES);
            }
        }
    }

    private void validateRoleChange(User user, Role newRole) {
        // Leaving TECH_LEAD requires handing over every team first
        if (user.getRole() == Role.TECH_LEAD && teamRepository.existsByTechLeadId(user.getId())) {
            throw new ApiException.BusinessRuleException(ErrorCode.TECH_LEAD_STILL_ASSIGNED);
        }
        // Demoting the last active admin would lock the system out
        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN
                && user.isActive()
                && countActiveAdmins() <= MIN_ACTIVE_ADMINS) {
            throw new ApiException.BusinessRuleException(ErrorCode.LAST_ACTIVE_ADMIN);
        }
    }

    private void validateDeactivatable(User user) {
        if (!user.isActive()) {
            throw new ApiException.BusinessRuleException(ErrorCode.USER_ALREADY_INACTIVE);
        }
        if (user.getRole() == Role.ADMIN && countActiveAdmins() <= MIN_ACTIVE_ADMINS) {
            throw new ApiException.BusinessRuleException(ErrorCode.LAST_ACTIVE_ADMIN);
        }
    }

    // ADMIN and HR see everyone, TECH_LEAD sees their team members, EMPLOYEE sees only self
    private void validateInScope(UUID targetUserId) {
        Role callerRole = CurrentActor.requireRole();
        UUID callerId = CurrentActor.requireUserId();

        if (callerRole == Role.ADMIN || callerRole == Role.HR || callerId.equals(targetUserId)) {
            return;
        }
        if (callerRole == Role.TECH_LEAD && ledTeamIds(callerId).stream()
                .anyMatch(teamId -> teamMemberRepository.existsByUserIdAndTeamId(targetUserId, teamId))) {
            return;
        }
        throw new ApiException.BusinessRuleException(ErrorCode.OUT_OF_SCOPE);
    }

    // ---------- Private helpers ----------

    private Page<User> queryInScope(String pattern,
                                    Role role,
                                    AccountStatus status,
                                    UUID departmentId,
                                    Pageable pageable) {
        Role callerRole = CurrentActor.requireRole();
        UUID callerId = CurrentActor.requireUserId();

        if (callerRole == Role.ADMIN || callerRole == Role.HR) {
            return userRepository.search(pattern, role, status, departmentId, pageable);
        }
        if (callerRole == Role.TECH_LEAD) {
            List<UUID> teamIds = ledTeamIds(callerId);
            return teamIds.isEmpty()
                    ? Page.empty(pageable)
                    : userRepository.searchByTeamIds(pattern, teamIds, role, status, departmentId, pageable);
        }

        // EMPLOYEE only ever sees the own record
        return userRepository.searchByIds(pattern, List.of(callerId), role, status, departmentId, pageable);
    }

    private List<UUID> ledTeamIds(UUID techLeadId) {
        return teamRepository.findByTechLeadId(techLeadId).stream().map(Team::getId).toList();
    }

    private void handOverLedTeams(UUID userId, List<Team> ledTeams, DeactivateUserRequest request) {
        if (ledTeams.isEmpty()) {
            return;
        }

        Map<UUID, UUID> replacementByTeamId = Optional.ofNullable(request)
                .map(DeactivateUserRequest::replacements)
                .orElse(List.of())
                .stream()
                .collect(Collectors.toMap(TeamReplacement::teamId,
                        TeamReplacement::replacementTechLeadId, (first, second) -> first));

        for (Team team : ledTeams) {
            UUID replacementId = replacementByTeamId.get(team.getId());
            if (replacementId == null) {
                throw new ApiException.BusinessRuleException(ErrorCode.REPLACEMENT_TECH_LEAD_REQUIRED);
            }
            requireValidReplacement(userId, replacementId);
            team.setTechLeadId(replacementId);
        }

        teamRepository.saveAll(ledTeams);
    }

    private void requireValidReplacement(UUID deactivatingUserId, UUID replacementId) {
        if (replacementId.equals(deactivatingUserId)) {
            throw new ApiException.BusinessRuleException(ErrorCode.INVALID_REPLACEMENT_TECH_LEAD);
        }
        User replacement = requireUser(replacementId);
        if (replacement.getRole() != Role.TECH_LEAD || !replacement.isActive()) {
            throw new ApiException.BusinessRuleException(ErrorCode.INVALID_REPLACEMENT_TECH_LEAD);
        }
    }

    // Memberships are rewritten wholesale so the single primary flag stays consistent
    private void replaceMemberships(UUID userId, List<TeamAssignment> assignments) {
        teamMemberRepository.deleteByUserId(userId);
        teamMemberRepository.flush();

        List<TeamMember> memberships = assignments.stream()
                .map(assignment -> TeamMember.builder()
                        .userId(userId)
                        .teamId(assignment.teamId())
                        .primaryTeam(assignment.primary())
                        .build())
                .toList();

        teamMemberRepository.saveAll(memberships);
    }

    // Department names and team memberships are resolved in bulk to avoid N+1
    private List<UserResponse> toResponses(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }

        Map<UUID, Department> departmentById = findDepartments(users.stream()
                .map(User::getPrimaryDepartmentId).collect(Collectors.toSet()));
        Map<UUID, List<UserTeamInfo>> teamsByUserId = findTeamInfos(users.stream()
                .map(User::getId).collect(Collectors.toSet()));

        return users.stream()
                .map(user -> toResponse(user,
                        departmentById.get(user.getPrimaryDepartmentId()),
                        teamsByUserId.getOrDefault(user.getId(), List.of()),
                        List.of()))
                .toList();
    }

    private Map<UUID, Department> findDepartments(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : departmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));
    }

    private Map<UUID, List<UserTeamInfo>> findTeamInfos(Collection<UUID> userIds) {
        List<TeamMember> memberships = teamMemberRepository.findByTeamIdIn(userIds);
        if (memberships.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Team> teamById = teamRepository.findByIdIn(memberships.stream()
                .map(TeamMember::getTeamId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));

        Map<UUID, List<UserTeamInfo>> result = new HashMap<>();
        for (TeamMember membership : memberships) {
            Team team = teamById.get(membership.getUserId());
            if (team == null) {
                continue;
            }
            result.computeIfAbsent(membership.getUserId(), key -> new ArrayList<>())
                    .add(new UserTeamInfo(team.getId(), team.getCode(), team.getName(),
                            membership.isPrimaryTeam()));
        }

        // Primary team first so the client can read it without scanning
        result.values().forEach(infos ->
                infos.sort(Comparator.comparing(UserTeamInfo::primary).reversed()
                        .thenComparing(UserTeamInfo::teamName, String.CASE_INSENSITIVE_ORDER)));
        return result;
    }

    private UserResponse getDetail(UUID id) {
        User user = requireUser(id);
        UserResponse response = toResponses(List.of(user)).get(0);
        return withLedTeams(response, teamRepository.findByTechLeadId(id));
    }

    private UserResponse withLedTeams(UserResponse response, List<Team> ledTeams) {
        List<TeamResponse> led = ledTeams.stream()
                .map(team -> new TeamResponse(team.getId(), team.getCode(), team.getName(),
                        team.getDescription(), team.getDepartmentId(), null, null,
                        team.getTechLeadId(), null, team.getDisplayOrder(), 0L))
                .toList();

        return new UserResponse(response.id(), response.fullName(), response.email(),
                response.username(), response.role(), response.status(),
                response.primaryDepartmentId(), response.departmentCode(), response.departmentName(),
                response.dateOfBirth(), response.phoneNumber(), response.address(),
                response.avatarImageId(), response.teams(), led,
                response.mustChangePassword(), response.createdAt());
    }

    private long countActiveAdmins() {
        return userRepository.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFoundException("user", id));
    }

    private void requireDepartmentExists(UUID departmentId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ApiException.NotFoundException("department", departmentId);
        }
    }

    // Audit snapshot: joined names and teams are not needed and would cost extra queries
    private UserResponse toFlatResponse(User user) {
        return toResponse(user, null, List.of(), List.of());
    }

    private UserResponse toResponse(User user,
                                    Department department,
                                    List<UserTeamInfo> teams,
                                    List<TeamResponse> ledTeams) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                user.getPrimaryDepartmentId(),
                department == null ? null : department.getCode(),
                department == null ? null : department.getName(),
                user.getDateOfBirth(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getAvatarImageId(),
                teams,
                ledTeams,
                user.isMustChangePassword(),
                user.getCreatedAt()
        );
    }
}

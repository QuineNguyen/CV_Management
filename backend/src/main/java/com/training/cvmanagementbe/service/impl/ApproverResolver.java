package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.entity.models.CvProfile;
import com.training.cvmanagementbe.entity.models.Team;
import com.training.cvmanagementbe.entity.models.TeamMember;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.approvals.AssignmentReason;
import com.training.cvmanagementbe.enums.approvals.AssignmentStatus;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.users.AccountStatus;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.ResolverResult;
import com.training.cvmanagementbe.repository.ApprovalAssignmentRepository;
import com.training.cvmanagementbe.repository.TeamMemberRepository;
import com.training.cvmanagementbe.repository.TeamRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Picks who reviews a draft and says why.
 * - Pure selection logic with no transaction of its own - it reads, decides and returns; the
 * caller owns the transaction and does all the writing. Keeping it that way means the algorithm is
 * unit-testable against repository stubs without a database.
 * - Two invariants make this tractable and both are guaranteed upstream: every employee belongs to
 * at least one team and every profile links to one of those teams (linked_team_id NOT NULL). Together
 * they mean a level-1 candidate always exists - so an empty list here is a broken invariant, not a
 * business case, and it blocks to submit rather than publishing with a "not reviewed" warning.
 */
@Component
@RequiredArgsConstructor
public class ApproverResolver {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final ApprovalAssignmentRepository approvalAssignmentRepository;

    // ---------- Level 1: Tech Lead ----------
    /*
     * Resolves the technical reviewer.
     * - Order of preference, each step only deciding when it narrows to exactly one person:
     * the lead of the team this profile is linked to, then the lead of the employee's primary team,
     * then the fewest open assignments, then whoever has waited longest for a turn.
     * - The employee is read off the profile rather than passed in. Today it equals
     * draft.ownerId and the submitter as well, but only the profile's own employee
     * is consistent with linked_team_id.
     *
     * @param profile:      the profile the CV belongs to - its linked team is the first preference
     *                      supplies both the employee and the first-preference team
     * @param submitterId:  excluded from the candidate list
     */
    public ResolverResult resolveLevel1(CvProfile profile, UUID submitterId) {
        UUID employeeId = profile.getEmployeeId();
        List<TeamMember> memberships = teamMemberRepository.findByUserIdIn(List.of(employeeId));
        Map<UUID, Team> teamsById = loadTeams(memberships);

        // Distinct: one person can lead several of the employee's teams.
        Set<UUID> techLeadIds = teamsById.values().stream()
                .map(Team::getTechLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<User> activeLeads = userRepository.findAllById(techLeadIds).stream()
                .filter(User::isActive)
                .toList();

        /*
         * Empty before the submitter is even considered means no team of this employee has an
         * active lead. It is a data defect: refuse to submit loudly instead of inventing a fallback
         * that would publish content nobody vouched for technically.
         */
        if (activeLeads.isEmpty()) {
            throw new ApiException.BusinessRuleException(ErrorCode.APPROVER_NOT_AVAILABLE);
        }

        List<User> eligible = activeLeads.stream()
                .filter(lead -> !lead.getId().equals(submitterId))
                .toList();

        Team linkedTeam = teamsById.get(profile.getLinkedTeamId());

        if (linkedTeam != null && submitterId.equals(linkedTeam.getTechLeadId())) {
            return ResolverResult.skipped(
                    AssignmentReason.LEVEL_1_SKIPPED_PROFILE_TEAM_LEAD.format(
                            linkedTeam.getName(), profile.getName()));
        }

        // Empty only because the submitter was removed: the one legitimate skip.
        if (eligible.isEmpty()) {
            return ResolverResult.skipped(
                    AssignmentReason.LEVEL_1_SKIPPED_SUBMITTER.format(submitterId)
            );
        }

        if (eligible.size() == 1) {
            return ResolverResult.assignedTo(
                    eligible.get(0).getId(),
                    AssignmentReason.SOLE_ELIGIBLE_LEAD.format()
            );
        }

        Set<UUID> eligibleIds = eligible.stream().map(User::getId).collect(Collectors.toSet());

        // (a) The team this profile is linked to - an AI Engineer CV goes to the AI lead.
        if (linkedTeam != null && eligibleIds.contains(linkedTeam.getTechLeadId())) {
            return ResolverResult.assignedTo(
                    linkedTeam.getTechLeadId(),
                    AssignmentReason.LINKED_TEAM_LEAD.format(linkedTeam.getName(), profile.getName())
            );
        }

        // (b) The employee's primary team.
        Team primaryTeam = memberships.stream()
                .filter(TeamMember::isPrimaryTeam)
                .map(membership -> teamsById.get(membership.getTeamId()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (primaryTeam != null && eligibleIds.contains(primaryTeam.getTechLeadId())) {
            return ResolverResult.assignedTo(
                    primaryTeam.getTechLeadId(),
                    AssignmentReason.PRIMARY_TEAM_LEAD.format(primaryTeam.getName())
            );
        }

        // (c) + (d) Fewest open assignments, ties broken by who has waited longest.
        return balanceLoad(eligible,
                AssignmentReason.LEAST_LOADED_LEAD,
                AssignmentReason.ROUND_ROBIN_LEAD);
    }

    /*
     * Resubmit path: keep the tech lead of the previous round when still available.
     * - The normal pipeline runs first, so a skip still wins: a submitter who became the linked
     * team's lead is not sent back to a colleague just because that colleague reviewed last time.
     * - "Available" means the same three filters the pipeline applies: active, still leading one of
     * the employee's current teams and not the submitter. A lead whose team the employee left
     * is not kept - team changes apply from the next round.
     *
     * @param previousAssigneeId    level-1 reviewer of the previous round; null falls through
     */
    public ResolverResult resolveLevel1(CvProfile profile, UUID submitterId, UUID previousAssigneeId) {
        ResolverResult fresh = resolveLevel1(profile, submitterId);

        if (previousAssigneeId == null || fresh.skipped()) {
            return fresh;
        }

        boolean stillEligible = !previousAssigneeId.equals(submitterId)
                && activeTechLeadIdsOf(profile.getEmployeeId()).contains(previousAssigneeId);

        return stillEligible
                ? ResolverResult.assignedTo(previousAssigneeId,
                AssignmentReason.STICKY_RESUBMIT_LEAD.format(previousAssigneeId))
                : fresh;
    }

    // ---------- Level 2: HR, falling back to Admin ----------
    /*
     * Resolves the format reviewer. Never skipped: candidates are every active HR other than the
     * submitter and when that set is empty it falls through to active Admins.
     * - No team preference applies here - at level 2 every HR is a peer, which is also why only
     * Admin may reassign at this level.
     */
    public ResolverResult resolveLevel2(UUID submitterId) {
        List<User> candidates = activeUsersOfRole(Role.HR, submitterId);

        if (!candidates.isEmpty()) {
            return balanceLoad(candidates,
                    AssignmentReason.LEAST_LOADED_HR,
                    AssignmentReason.ROUND_ROBIN_HR);
        }

        List<User> admins = activeUsersOfRole(Role.ADMIN, submitterId);
        if (admins.isEmpty()) {
            // Keeps at least one active Admin alive, so reaching here means the system is
            // already in a state no in-app action can repair.
            throw new ApiException.BusinessRuleException(ErrorCode.APPROVER_NOT_AVAILABLE);
        }

        ResolverResult picked = balanceLoad(admins,
                AssignmentReason.ADMIN_FALLBACK,
                AssignmentReason.ADMIN_FALLBACK);

        return ResolverResult.assignedTo(picked.assigneeId(), picked.reason());
    }

    /*
     * Level-2 counterpart of the sticky rule.
     * - The previous HR is kept only while they sit in the same pool the pipeline would draw from:
     * active HRs other than the submitter, or active Admins when no such HR exists. So an Admin
     * who covered a round while HR was empty does not keep the draft once an HR is back.
     *
     * @param previousAssigneeId    level-2 reviewer of the previous round; null falls through
     */
    public ResolverResult resolveLevel2(UUID submitterId, UUID previousAssigneeId) {
        if (previousAssigneeId != null) {
            List<User> hrs = activeUsersOfRole(Role.HR, submitterId);
            List<User> pool = hrs.isEmpty() ? activeUsersOfRole(Role.ADMIN, submitterId) : hrs;

            boolean stillEligible = pool.stream().anyMatch(user -> user.getId().equals(previousAssigneeId));
            if (stillEligible) {
                return ResolverResult.assignedTo(previousAssigneeId,
                        AssignmentReason.STICKY_RESUBMIT_HR.format(previousAssigneeId));
            }
        }
        return resolveLevel2(submitterId);
    }

    // ---------- Selection helpers ----------
    /*
     * Picks the least loaded candidate; among those tied on load, the one who has gone longest
     * without an assignment. Somebody never assigned before wins outright - they sort first because
     * a missing "last assigned" reads as the beginning of time.
     */
    private ResolverResult balanceLoad(List<User> candidates,
                                       AssignmentReason leastLoadedReason,
                                       AssignmentReason roundRobinReason) {
        List<UUID> ids = candidates.stream().map(User::getId).toList();

        Map<UUID, Long> openCounts = toCountMap(
                approvalAssignmentRepository.countByAssigneeIn(ids, AssignmentStatus.ASSIGNED)
        );
        Map<UUID, LocalDateTime> lastAssigned = toTimestampMap(
                approvalAssignmentRepository.lastAssignedAtByAssigneeIn(ids)
        );

        long minLoad = ids.stream()
                .mapToLong(id -> openCounts.getOrDefault(id, 0L))
                .min()
                .orElse(0L);

        List<UUID> leastLoaded = ids.stream()
                .filter(id -> openCounts.getOrDefault(id, 0L) == minLoad)
                .toList();

        if (leastLoaded.size() == 1) {
            return ResolverResult.assignedTo(
                    leastLoaded.get(0),
                    leastLoadedReason.format(minLoad, candidates.size())
            );
        }

        // LocalDateTime.MIN for "never assigned", so these candidates sort ahead of everyone.
        UUID idlest = leastLoaded.stream()
                .min(Comparator
                        .comparing((UUID id) -> lastAssigned.getOrDefault(id, LocalDateTime.MIN))
                        // Stable ordering when two people are also tied on timestamp, so the same
                        // inputs always produce the same pick and the tests are deterministic.
                        .thenComparing(UUID::toString))
                .orElseThrow();

        return ResolverResult.assignedTo(
                idlest,
                roundRobinReason.format(leastLoaded.size(), minLoad)
        );
    }

    private List<User> activeUsersOfRole(Role role, UUID excludedUserId) {
        return userRepository.findByRoleAndStatusOrderByFullNameAsc(role, AccountStatus.ACTIVE)
                .stream()
                .filter(user -> !user.getId().equals(excludedUserId))
                .toList();
    }

    // Active leads of every team the employee currently belongs to.
    private Set<UUID> activeTechLeadIdsOf(UUID employeeId) {
        List<TeamMember> memberships = teamMemberRepository.findByUserIdIn(List.of(employeeId));

        Set<UUID> leadIds = loadTeams(memberships).values().stream()
                .map(Team::getTechLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return userRepository.findAllById(leadIds).stream()
                .filter(User::isActive)
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    private Map<UUID, Team> loadTeams(List<TeamMember> memberships) {
        Set<UUID> teamIds = memberships.stream()
                .map(TeamMember::getTeamId)
                .collect(Collectors.toSet());

        return teamIds.isEmpty()
                ? Map.of()
                : teamRepository.findByIdIn(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    private Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private Map<UUID, LocalDateTime> toTimestampMap(List<Object[]> rows) {
        Map<UUID, LocalDateTime> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((UUID) row[0], (LocalDateTime) row[1]);
        }
        return result;
    }
}

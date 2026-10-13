package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.constant.DashboardDefaults;
import com.training.cvmanagementbe.dto.response.dashboard.*;
import com.training.cvmanagementbe.entity.models.ApprovalAssignment;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.CvDraft;
import com.training.cvmanagementbe.entity.models.UpdateRequest;
import com.training.cvmanagementbe.enums.approvals.AssignmentStatus;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;
import com.training.cvmanagementbe.enums.users.AccountStatus;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.dashboard.DeadlineWindow;
import com.training.cvmanagementbe.repository.*;
import com.training.cvmanagementbe.repository.projection.DeadlineBucketCount;
import com.training.cvmanagementbe.service.DashboardService;
import com.training.cvmanagementbe.service.ProfileUpdateRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Every figure is computed here, at request time, from the source tables.
 *
 * - Deadline widgets compare update_requests.deadline with now and never read reminder_logs:
 * They replace the HR monitoring email, so they must be right whenever the page opens.
 * - One DeadlineWindow per call feeds both the counts and the rows, so they always agree.
 * - Scope is decided here, not in the controller: The same rule as every other read.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final CvRepository cvRepository;
    private final CvProfileRepository cvProfileRepository;
    private final CvDraftRepository cvDraftRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UpdateRequestRepository updateRequestRepository;
    private final ApprovalAssignmentRepository approvalAssignmentRepository;
    private final ProfileUpdateRequestService profileUpdateRequestService;

    // ---------- Dashboards ----------

    @Override
    public AdminDashboardResponse getAdminDashboard() {
        requireRole(Role.ADMIN, Role.HR);

        LocalDateTime now = LocalDateTime.now();
        UUID actorId = CurrentActor.requireUserId();

        long activeCvs = cvRepository.countByLifecycleStatus(LifecycleStatus.ACTIVE);
        long cvsWithVersion = cvRepository.countWithVersionByLifecycleStatus(LifecycleStatus.ACTIVE);

        return new AdminDashboardResponse(
                userRepository.countByStatus(AccountStatus.ACTIVE),
                activeCvs,
                cvsWithVersion,
                activeCvs - cvsWithVersion,
                updateRequestRepository.countByStatus(RequestStatus.PENDING),
                // Same scoped count as the sidebar badge: HR never sees a number it cannot open
                profileUpdateRequestService.countPending(),
                approvalAssignmentRepository.countByStatus(AssignmentStatus.ASSIGNED),
                approvalAssignmentRepository.countByAssigneeIdAndStatus(actorId, AssignmentStatus.ASSIGNED),
                requestDeadlines(null, now),
                approvalSla(now),
                now
        );
    }

    @Override
    public TechLeadDashboardResponse getTechLeadDashboard() {
        requireRole(Role.TECH_LEAD);

        LocalDateTime now = LocalDateTime.now();
        UUID actorId = CurrentActor.requireUserId();

        /*
         * Resolved through team_members, never through the department tree.
         * Active accounts only, the same rule as the company-wide employee count.
         */
        List<UUID> memberIds = teamMemberRepository.findMemberIdsOfLedTeams(actorId, AccountStatus.ACTIVE);

        return new TechLeadDashboardResponse(
                memberIds.size(),
                countActiveCvsOf(memberIds),
                countPendingRequestsOf(memberIds),
                approvalAssignmentRepository.countByAssigneeIdAndStatus(actorId, AssignmentStatus.ASSIGNED),
                approvalAssignmentRepository.countByAssigneeIdAndStatusAndDueAtBefore(actorId, AssignmentStatus.ASSIGNED, now),
                requestDeadlines(memberIds, now),
                now
        );
    }

    @Override
    public EmployeeDashboardResponse getEmployeeDashboard() {
        LocalDateTime now = LocalDateTime.now();
        UUID actorId = CurrentActor.requireUserId();
        List<UUID> self = List.of(actorId);

        return new EmployeeDashboardResponse(
                countActiveCvsOf(self),
                cvDraftRepository.countByOwnerIdAndStatusIn(actorId, CvDraft.OPEN_STATUSES),
                // LOCKED = submitted and waiting for a reviewer
                cvDraftRepository.countByOwnerIdAndStatusIn(actorId, CvDraft.LOCKED_STATUSES),
                countPendingRequestsOf(self),
                requestDeadlines(self, now),
                now
        );
    }

    // ---------- Widgets ----------

    /*
     * employeeIds = null means the whole company; an empty set means nobody in scope,
     * answered without a query (an empty IN list is not valid SQL on MariaDB).
     */
    private RequestDeadlineWidget requestDeadlines(Collection<UUID> employeeIds, LocalDateTime now) {
        DeadlineWindow window = DeadlineWindow.of(now, DashboardDefaults.APPROACHING_DAYS);
        if (employeeIds != null && employeeIds.isEmpty()) {
            return RequestDeadlineWidget.empty(window.approachingDays());
        }

        Pageable top = PageRequest.of(0, DashboardDefaults.WIDGET_ROW_LIMIT);

        DeadlineBucketCount buckets = employeeIds == null
                ? updateRequestRepository.countDeadlineBuckets(
                        RequestStatus.PENDING, window.now(), window.lastDayEnd(), window.horizon())
                : updateRequestRepository.countDeadlineBucketsFor(
                        employeeIds, RequestStatus.PENDING, window.now(), window.lastDayEnd(), window.horizon()
        );

        List<UpdateRequest> rows = employeeIds == null
                ? updateRequestRepository.findMostUrgent(RequestStatus.PENDING, window.horizon(), top)
                : updateRequestRepository.findMostUrgentFor(employeeIds, RequestStatus.PENDING, window.horizon(), top);

        return new RequestDeadlineWidget(
                window.approachingDays(),
                orZero(buckets.getApproaching()),
                orZero(buckets.getLastDay()),
                orZero(buckets.getOverdue()),
                toRequestSummaries(rows, window)
        );
    }

    // Admin/HR only: The people who can reassign a stalled review
    private ApprovalSlaWidget approvalSla(LocalDateTime now) {
        Pageable top = PageRequest.of(0, DashboardDefaults.WIDGET_ROW_LIMIT);

        List<ApprovalAssignment> rows = approvalAssignmentRepository
                .findByStatusAndDueAtBeforeOrderByDueAtAsc(AssignmentStatus.ASSIGNED, now, top);

        return new ApprovalSlaWidget(
                approvalAssignmentRepository.countByStatusAndDueAtBefore(AssignmentStatus.ASSIGNED, now),
                toApprovalSummaries(rows, now)
        );
    }

    // ---------- Scoped counts ----------

    private long countActiveCvsOf(Collection<UUID> employeeIds) {
        return employeeIds.isEmpty()
                ? 0
                : cvRepository.countByOwnersAndLifecycleStatus(employeeIds, LifecycleStatus.ACTIVE);
    }

    private long countPendingRequestsOf(Collection<UUID> employeeIds) {
        return employeeIds.isEmpty()
                ? 0
                : updateRequestRepository.countByStatusAndEmployeeIdIn(RequestStatus.PENDING, employeeIds);
    }

    // ---------- Mapping ----------

    // Names for the whole page in two queries, not one per row
    private List<OverdueRequestSummary> toRequestSummaries(List<UpdateRequest> rows, DeadlineWindow window) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> employeeNames = loadUserNames(rows.stream().map(UpdateRequest::getEmployeeId));
        Map<UUID, String> profileNames = loadProfileNames(rows.stream().map(UpdateRequest::getProfileId));

        return rows.stream()
                .map(row -> new OverdueRequestSummary(
                        row.getId(),
                        row.getCvId(),
                        employeeNames.get(row.getEmployeeId()),
                        profileNames.get(row.getProfileId()),
                        row.getLanguage(),
                        row.getDeadline(),
                        window.daysOverdue(row.getDeadline()),
                        window.classify(row.getDeadline())
                ))
                .toList();
    }

    private List<ApprovalOverdueSummary> toApprovalSummaries(List<ApprovalAssignment> rows, LocalDateTime now) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<UUID> draftIds = rows.stream().map(ApprovalAssignment::getDraftId).collect(Collectors.toSet());
        Map<UUID, UUID> ownerByDraft = new HashMap<>();
        cvDraftRepository.findAllById(draftIds)
                .forEach(draft -> ownerByDraft.put(draft.getId(), draft.getOwnerId()));

        Map<UUID, String> names = loadUserNames(Stream.concat(
                ownerByDraft.values().stream(),
                rows.stream().map(ApprovalAssignment::getAssigneeId)
        ));

        return rows.stream()
                .map(row -> new ApprovalOverdueSummary(
                        row.getId(),
                        names.get(ownerByDraft.get(row.getDraftId())),
                        names.get(row.getAssigneeId()),
                        row.getLevel(),
                        row.getDueAt(),
                        Duration.between(row.getDueAt(), now).toDays()
                ))
                .toList();
    }

    // HashMap, so a null key (no profile, missing draft) simply reads as null
    private Map<UUID, String> loadUserNames(Stream<UUID> userIds) {
        Set<UUID> ids = userIds.filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> names.put(user.getId(), user.getFullName()));
        }
        return names;
    }

    private Map<UUID, String> loadProfileNames(Stream<UUID> profileIds) {
        Set<UUID> ids = profileIds.filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            cvProfileRepository.findAllById(ids).forEach(profile -> names.put(profile.getId(), profile.getName()));
        }
        return names;
    }

    // ---------- Access control ----------

    private void requireRole(Role... allowed) {
        if (!List.of(allowed).contains(CurrentActor.requireRole())) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}

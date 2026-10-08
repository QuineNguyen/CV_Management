package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.cvs.BatchPreviewRequest;
import com.training.cvmanagementbe.dto.request.cvs.CreateBatchRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.*;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.cvs.*;
import com.training.cvmanagementbe.enums.users.AccountStatus;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.BatchRecipient;
import com.training.cvmanagementbe.repository.*;
import com.training.cvmanagementbe.repository.projection.BatchStatusCount;
import com.training.cvmanagementbe.service.BatchRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchRequestServiceImpl implements BatchRequestService {

    // Deadlines are calendar days in Vietnam, whatever zone the server runs in
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Not LocalTime.MAX: DATETIME keeps no fraction and would round up to the next day
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);
    // Unique index on the pending (employee, profile, language) slot
    private static final String PENDING_SLOT_INDEX = "uk_update_requests_pending";
    // A concurrent create can take a slot after our lookup; each attempt re-reads the slots
    private static final int MAX_CREATE_ATTEMPTS = 3;
    // Department batches reach CV owners; Admin/HR are the senders
    private static final Set<Role> DEPARTMENT_ROLES = EnumSet.of(Role.EMPLOYEE, Role.TECH_LEAD);
    private static final String UNKNOWN_TARGET = "-";
    private static final String MANUAL_LABEL = "%d selected employee(s)";
    private static final Comparator<BatchRecipient> BY_NAME =
            Comparator.comparing(BatchRecipient::fullName, String.CASE_INSENSITIVE_ORDER);
    private static final Set<BatchRequestStatus> FINISHED =
            EnumSet.of(BatchRequestStatus.COMPLETED, BatchRequestStatus.COMPLETED_WITH_ERRORS);

    private final BatchRequestRepository batchRequestRepository;
    private final UpdateRequestRepository updateRequestRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final CvProfileRepository cvProfileRepository;
    private final CvRepository cvRepository;
    private final AnchoredNoteCodec anchoredNoteCodec;
    private final BatchTargetCodec targetCodec;
    private final BatchWorker batchWorker;
    private final AuditLogger auditLogger;
    private final TransactionTemplate transactionTemplate;
    private final UpdateRequestCanceller updateRequestCanceller;

    // ---------- Preview ----------

    // An empty "included" list is a valid answer: The screen disables "Create batch" on it
    @Override
    public BatchPreviewResponse preview(BatchPreviewRequest request, Pageable includedPage, Pageable excludedPage) {
        requireAdminOrHr();
        toDeadline(request.deadline());

        List<BatchRecipient> recipients = resolve(request);
        List<PreviewEmployee> included = recipients.stream()
                .filter(BatchRecipient::included)
                .sorted(BY_NAME)
                .map(r -> new PreviewEmployee(r.fullName(), r.departmentName(), r.profileName()))
                .toList();
        List<ExcludedEmployee> excluded = recipients.stream()
                .filter(r -> !r.included())
                .sorted(BY_NAME)
                .map(r -> new ExcludedEmployee(r.fullName(), r.exclusion()))
                .toList();

        return new BatchPreviewResponse(slice(included, includedPage), slice(excluded, excludedPage));
    }

    // ---------- Create ----------

    /*
     * Each attempt is its own transaction. A unique-index hit means another creation took a slot after
     * our lookup; the failed flush poisons the whole transaction, so the attempt is rolled back and
     * redone - the next lookup skips that employee and total_count shrinks accordingly.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BatchRequestResponse create(CreateBatchRequest request) {
        requireAdminOrHr();
        LocalDateTime deadline = toDeadline(request.deadline());

        for (int attempt = 1; ; attempt++) {
            try {
                UUID batchId = transactionTemplate.execute(status -> createOnce(request, deadline));
                return getDetail(batchId);
            } catch (DataIntegrityViolationException e) {
                if (!violates(e, PENDING_SLOT_INDEX)) {
                    throw e;
                }
                if (attempt == MAX_CREATE_ATTEMPTS) {
                    throw new ApiException.BusinessRuleException(ErrorCode.BATCH_PREVIEW_OUTDATED);
                }
                log.info("Pending slot taken concurrently, retrying batch create ({}/{})", attempt, MAX_CREATE_ATTEMPTS);
            }
        }
    }

    private UUID createOnce(CreateBatchRequest request, LocalDateTime deadline) {
        // Recomputed server-side: The list the screen showed is never trusted
        List<BatchRecipient> included = resolve(request.toPreview()).stream()
                .filter(BatchRecipient::included)
                .toList();

        // An empty batch is refused before any row is written
        if (included.isEmpty()) {
            throw new ApiException.BusinessRuleException(ErrorCode.BATCH_EMPTY);
        }
        // More recipients than confirmed: Someone the user has not reviewed would be asked
        if (included.size() > request.expectedCount()) {
            throw new ApiException.BusinessRuleException(ErrorCode.BATCH_PREVIEW_OUTDATED);
        }

        BatchRequest batch = batchRequestRepository.save(newBatch(request, deadline, included.size()));
        Map<UUID, UUID> activeCvIds = activeCvIdsByProfile(included, request.language());
        updateRequestRepository.saveAllAndFlush(included.stream()
                .map(recipient -> newChild(batch, recipient, activeCvIds))
                .toList());

        UUID batchId = batch.getId();
        auditLogger.record(Action.CREATE_BATCH_REQUEST, TargetType.BATCH_UPDATE_REQUEST, batchId, null, toResponse(batch));

        // The worker must read committed rows, so it starts only after this transaction commits
        afterCommit(() -> batchWorker.process(batchId));
        return batchId;
    }

    // ---------- Queries ----------

    @Override
    public BatchRequestResponse getDetail(UUID batchId) {
        requireAdminOrHr();
        return toResponse(requireBatch(batchId));
    }

    @Override
    public PagedResponse<BatchFailedItemResponse> getFailedItems(UUID batchId, Pageable pageable) {
        requireAdminOrHr();
        requireBatch(batchId);

        Page<UpdateRequest> page = updateRequestRepository.findFailedByBatch(batchId, pageable);
        // One lookup per page for names and profiles, not one per row
        Map<UUID, User> users = userRepository.findAllById(idsOf(page.getContent(), UpdateRequest::getEmployeeId))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, String> profileNames = cvProfileRepository.findAllById(idsOf(page.getContent(), UpdateRequest::getProfileId))
                .stream()
                .collect(Collectors.toMap(CvProfile::getId, CvProfile::getName));

        List<BatchFailedItemResponse> content = page.getContent().stream()
                .map(request -> {
                    User employee = users.get(request.getEmployeeId());
                    return new BatchFailedItemResponse(
                            request.getId(),
                            employee == null ? null : employee.getFullName(),
                            employee == null ? null : employee.getEmail(),
                            request.getProfileId() == null ? null : profileNames.get(request.getProfileId())
                    );
                })
                .toList();
        return PagedResponse.of(page, content);
    }

    @Override
    public PagedResponse<BatchRequestResponse> list(BatchRequestStatus status, Pageable pageable) {
        requireAdminOrHr();
        Page<BatchRequest> page = status == null
                ? batchRequestRepository.findAll(pageable)
                : batchRequestRepository.findByStatus(status, pageable);
        return PagedResponse.of(page, toResponses(page.getContent()));
    }

    // ---------- Resend ----------

    // Only the flagged children go back to the queue; no new child is created
    @Override
    @Transactional
    public BatchRequestResponse resendFailed(UUID batchId) {
        requireAdminOrHr();
        BatchRequest batch = requireBatch(batchId);

        // Reopening would bring a cancelled batch back to life
        if (batch.getStatus() == BatchRequestStatus.CANCELLED) {
            throw new ApiException.BusinessRuleException(ErrorCode.BATCH_ALREADY_CANCELLED);
        }
        if (batch.getStatus() == BatchRequestStatus.PROCESSING) {
            throw new ApiException.BusinessRuleException(ErrorCode.BATCH_STILL_PROCESSING);
        }
        List<UUID> failedIds = updateRequestRepository.findFailedIdsByBatch(batchId);
        if (failedIds.isEmpty()) {
            throw new ApiException.BusinessRuleException(ErrorCode.BATCH_NO_FAILED_EMAILS);
        }

        // Back to PROCESSING; processed restarts below the total so the progress bar runs again
        if (batchRequestRepository.reopenForResend(batchId, failedIds.size(), BatchRequestStatus.PROCESSING, FINISHED) == 0) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        afterCommit(() -> batchWorker.processFailedOnly(batchId, failedIds));
        return toResponse(requireBatch(batchId));
    }

    // ---------- Cancel ----------

    /*
     * Cancels the batch and every child still PENDING; completed children and drafts are kept.
     * Scope is checked before status, so a caller outside it learns nothing about the batch.
     */
    @Override
    @Transactional
    public BatchCancelResponse cancel(UUID batchId) {
        BatchRequest batch = requireBatch(batchId);
        Role role = CurrentActor.requireRole();
        UUID actorId = CurrentActor.requireUserId();

        if (!mayCancel(batch, role, actorId)) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
        if (batch.getStatus() == BatchRequestStatus.CANCELLED) {
            throw new ApiException.ConflictException(ErrorCode.BATCH_ALREADY_CANCELLED);
        }

        BatchRequestResponse before = toResponse(batch);
        BatchRequestStatus previousStatus = batch.getStatus();
        LocalDateTime now = LocalDateTime.now();

        // CAS first: A concurrent cancel stops here instead of notifying everyone twice
        if (batchRequestRepository.cancelBatch(batchId, BatchRequestStatus.CANCELLED, actorId, now) == 0) {
            throw new ApiException.ConflictException(ErrorCode.BATCH_ALREADY_CANCELLED);
        }

        List<UpdateRequest> cancelled = updateRequestCanceller.cancelPendingForBatch(batchId, actorId, now);

        // Nothing left to stop: The exception rolls the CAS back too
        if (cancelled.isEmpty() && previousStatus != BatchRequestStatus.PROCESSING) {
            throw new ApiException.BusinessRuleException(ErrorCode.BATCH_NO_PENDING_REQUESTS);
        }

        BatchRequestResponse after = toResponse(requireBatch(batchId));
        auditLogger.record(Action.CANCEL_BATCH_REQUEST, TargetType.BATCH_UPDATE_REQUEST, batchId, before, after);
        return new BatchCancelResponse(after, cancelled.size());
    }

    // ---------- Resolution ----------

    /*
     * Recipients in criteria order, each with the primary profile and why it is left out, if it is.
     * Runs on every preview page and again on create.
     * The language is a Language (VI/EN/JA): "ALL" never reaches here, the JSON binding refuses it.
     */
    private List<BatchRecipient> resolve(BatchPreviewRequest request) {
        List<User> candidates = switch (request.targetType()) {
            case DEPARTMENT -> departmentMembers(singleTarget(request));
            case TEAM -> teamMembers(singleTarget(request));
            case MANUAL -> manualPicks(request.targetIds());
        };
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Admin/HT edit their own CV directly, so the caller is skipped, never asked
        UUID currentActorId = CurrentActor.requireUserId();
        Set<UUID> employeeIds = candidates.stream().map(User::getId).collect(Collectors.toSet());
        Map<UUID, CvProfile> primaryProfiles = primaryProfilesOf(employeeIds);
        Map<UUID, Set<UUID>> pendingProfiles = pendingProfilesOf(employeeIds, request.language());
        Map<UUID, String> departmentNames = departmentNamesOf(candidates);

        Set<UUID> seen = new HashSet<>();
        List<BatchRecipient> recipients = new ArrayList<>();
        for (User user : candidates) {
            CvProfile profile = primaryProfiles.get(user.getId());
            UUID profileId = profile == null ? null : profile.getId();
            recipients.add(new BatchRecipient(
                    user.getId(),
                    user.getFullName(),
                    departmentNames.get(user.getPrimaryDepartmentId()),
                    profileId,
                    profile == null ? null : profile.getName(),
                    exclusionOf(user.getId(), profileId, seen, pendingProfiles, currentActorId)
            ));
        }
        return recipients;
    }

    /*
     * Checked in this order:
     * - SELF_REQUEST first: Whatever else is true, the real reason is "you cannot ask yourself".
     * - DUPLICATE_IN_BATCH: The first occurrence wins.
     * - ALREADY_PENDING: "no profile" is one value of the slot key, same as the unique index.
     */
    private BatchExclusionReason exclusionOf(UUID employeeId, UUID profileId,
                                             Set<UUID> seen, Map<UUID, Set<UUID>> pendingProfiles,
                                             UUID currentActorId) {
        if (employeeId.equals(currentActorId)) {
            return BatchExclusionReason.SELF_REQUEST;
        }
        if (!seen.add(employeeId)) {
            return BatchExclusionReason.DUPLICATE_IN_BATCH;
        }
        if (pendingProfiles.getOrDefault(employeeId, Collections.emptySet()).contains(profileId)) {
            return BatchExclusionReason.ALREADY_PENDING;
        }
        return null;
    }

    // The department and all its sub-departments, active employees and tech leads
    private List<User> departmentMembers(UUID departmentId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ApiException.NotFoundException("department", departmentId);
        }
        Set<UUID> departmentIds = departmentRepository.findSelfAndDescendantIds(departmentId);
        return userRepository.findByPrimaryDepartmentIdInAndStatusAndRoleIn(
                departmentIds, AccountStatus.ACTIVE, DEPARTMENT_ROLES
        );
    }

    // The tech lead is already a member, so no separate join on tech_lead_id
    private List<User> teamMembers(UUID teamId) {
        if (!teamRepository.existsById(teamId)) {
            throw new ApiException.NotFoundException("team", teamId);
        }
        Collection<UUID> memberIds = userRepository.findUserIdsByTeamIds(List.of(teamId));
        return memberIds.isEmpty()
                ? List.of()
                : userRepository.findAllByIdInAndStatus(memberIds, AccountStatus.ACTIVE);
    }

    // Every pick must exist and be active; repeats are kept so the preview can name them
    private List<User> manualPicks(List<UUID> ids) {
        Map<UUID, User> found = userRepository.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<User> picks = new ArrayList<>();
        for (UUID id : ids) {
            User user = found.get(id);
            if (user == null) {
                throw new ApiException.NotFoundException("user", id);
            }
            if (!user.isActive()) {
                throw new ApiException.BusinessRuleException(ErrorCode.EMPLOYEE_NOT_ACTIVE);
            }
            picks.add(user);
        }
        return picks;
    }

    private UUID singleTarget(BatchPreviewRequest request) {
        if (request.targetIds().size() != 1) {
            throw new ApiException.BusinessRuleException(ErrorCode.BATCH_INVALID_TARGET);
        }
        return request.targetIds().get(0);
    }

    // Batches always target the primary profile
    private Map<UUID, CvProfile> primaryProfilesOf(Set<UUID> employeeIds) {
        return cvProfileRepository.findByEmployeeIdInAndLifecycleStatus(employeeIds, LifecycleStatus.ACTIVE).stream()
                .filter(CvProfile::isPrimary)
                .collect(Collectors.toMap(CvProfile::getEmployeeId, Function.identity(), (first, second) -> first));
    }

    // Profile ids (null included) that already hold a pending slot, per employee
    private Map<UUID, Set<UUID>> pendingProfilesOf(Set<UUID> employeeIds, Language language) {
        return updateRequestRepository
                .findByEmployeeIdInAndLanguageAndStatus(employeeIds, language, RequestStatus.PENDING)
                .stream()
                .collect(Collectors.groupingBy(UpdateRequest::getEmployeeId,
                        Collectors.mapping(UpdateRequest::getProfileId, Collectors.toSet())));
    }

    private Map<UUID, String> departmentNamesOf(List<User> users) {
        Set<UUID> ids = users.stream().map(User::getPrimaryDepartmentId).collect(Collectors.toSet());
        return departmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName)
        );
    }

    // Only an ACTIVE CV is linked; a deleted one holds no slot
    private Map<UUID, UUID> activeCvIdsByProfile(List<BatchRecipient> recipients, Language language) {
        Set<UUID> profileIds = recipients.stream()
                .map(BatchRecipient::profileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        return cvRepository.findByProfileIdInAndLanguageAndLifecycleStatus(profileIds, language, LifecycleStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(Cv::getProfileId, Cv::getId, (first, second) -> first));
    }

    // ---------- Writing ----------

    private BatchRequest newBatch(CreateBatchRequest request, LocalDateTime deadline, int total) {
        BatchRequest batch = new BatchRequest();
        batch.setReason(request.reason().trim());
        batch.setDeadline(deadline);
        batch.setLanguage(request.language());
        batch.setTargetType(request.targetType());
        batch.setTargetValue(targetCodec.write(request.targetIds()));
        // Locked after exclusion: The progress bar's denominator
        batch.setTotalCount(total);
        batch.setProcessedCount(0);
        batch.setErrorCount(0);
        batch.setStatus(BatchRequestStatus.PROCESSING);
        return batch;
    }

    private UpdateRequest newChild(BatchRequest batch, BatchRecipient recipient, Map<UUID, UUID> activeCvIds) {
        UpdateRequest child = new UpdateRequest();
        child.setEmployeeId(recipient.employeeId());
        // Primary profile or none: "create a profile and a CV"
        child.setProfileId(recipient.profileId());
        child.setLanguage(batch.getLanguage());
        child.setCvId(recipient.profileId() == null ? null : activeCvIds.get(recipient.profileId()));
        child.setReason(batch.getReason());
        child.setAnchoredNotes(anchoredNoteCodec.write(List.of()));
        child.setDeadline(batch.getDeadline());
        child.setStatus(RequestStatus.PENDING);
        child.setBatchRequestId(batch.getId());
        child.setNotificationFailed(false);
        return child;
    }

    // ---------- Validation ----------

    private BatchRequest requireBatch(UUID batchId) {
        return batchRequestRepository.findById(batchId)
                .orElseThrow(() -> new ApiException.NotFoundException("batch request", batchId));
    }

    private void requireAdminOrHr() {
        Role role = CurrentActor.requireRole();
        if (role != Role.ADMIN && role != Role.HR) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
    }

    // Today is allowed and means 23:59:59 today
    private LocalDateTime toDeadline(LocalDate date) {
        if (date.isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw new ApiException.BusinessRuleException(ErrorCode.DEADLINE_IN_PAST);
        }
        return date.atTime(END_OF_DAY);
    }

    // ---------- Private helpers ----------

    private BatchRequestResponse toResponse(BatchRequest batch) {
        return toResponses(List.of(batch)).get(0);
    }

    // One lookup per kind of name per page, not one per row
    private List<BatchRequestResponse> toResponses(List<BatchRequest> batches) {
        if (batches.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> targetIds = batches.stream()
                .collect(Collectors.toMap(BatchRequest::getId, batch -> targetCodec.read(batch.getTargetValue())));

        Map<UUID, String> departmentNames = departmentRepository
                .findAllById(firstTargetsOf(batches, targetIds, BatchTargetType.DEPARTMENT)).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        Map<UUID, String> teamNames = teamRepository
                .findAllById(firstTargetsOf(batches, targetIds, BatchTargetType.TEAM)).stream()
                .collect(Collectors.toMap(Team::getId, Team::getName));

        Set<UUID> creatorIds = batches.stream()
                .map(BatchRequest::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> creatorNames = userRepository.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        Map<UUID, Map<RequestStatus, Long>> childCounts = updateRequestRepository
                .countByBatchAndStatus(targetIds.keySet()).stream()
                .collect(Collectors.groupingBy(BatchStatusCount::getBatchRequestId,
                        Collectors.toMap(BatchStatusCount::getStatus, BatchStatusCount::getRequestCount)));

        // Resolved once per page: Which rows offer a cancel action to this caller
        Role role = CurrentActor.requireRole();
        UUID actorId = CurrentActor.requireUserId();

        return batches.stream()
                .map(batch -> {
                    Map<RequestStatus, Long> counts = childCounts.getOrDefault(batch.getId(), Map.of());
                    int pending = counts.getOrDefault(RequestStatus.PENDING, 0L).intValue();
                    int completed = counts.getOrDefault(RequestStatus.COMPLETED, 0L).intValue();
                    return new BatchRequestResponse(
                            batch.getId(),
                            batch.getTargetType(),
                            targetLabel(batch, targetIds.get(batch.getId()), departmentNames, teamNames),
                            batch.getLanguage(),
                            batch.getReason(),
                            batch.getDeadline(),
                            batch.getTotalCount(),
                            batch.getProcessedCount(),
                            batch.getErrorCount(),
                            batch.getStatus(),
                            batch.getCreatedAt(),
                            batch.getCreatedBy() == null ? null : creatorNames.get(batch.getCreatedBy()),
                            pending,
                            completed,
                            isCancellable(batch, pending, role, actorId)
                    );
                })
                .toList();
    }

    // Same rule cancel() enforces, so the button never offers a refused action
    private boolean isCancellable(BatchRequest batch, int pending, Role role, UUID actorId) {
        return batch.getStatus() != BatchRequestStatus.CANCELLED
                && mayCancel(batch, role, actorId)
                && (pending > 0 || batch.getStatus() == BatchRequestStatus.PROCESSING);
    }

    // Admin cancels any batch, HR only their own - same split as a single request
    private boolean mayCancel(BatchRequest batch, Role role, UUID actorId) {
        return role == Role.ADMIN || (role == Role.HR && actorId.equals(batch.getCreatedBy()));
    }

    // Department and team batches hold exactly one id
    private Set<UUID> firstTargetsOf(List<BatchRequest> batches, Map<UUID, List<UUID>> targetIds,
                                     BatchTargetType type) {
        return batches.stream()
                .filter(batch -> batch.getTargetType() == type)
                .map(batch -> targetIds.get(batch.getId()).get(0))
                .collect(Collectors.toSet());
    }

    // Display name of the criteria; the ids stay in target_value
    private String targetLabel(BatchRequest batch, List<UUID> ids,
                               Map<UUID, String> departmentNames, Map<UUID, String> teamNames) {
        return switch (batch.getTargetType()) {
            case DEPARTMENT -> departmentNames.getOrDefault(ids.get(0), UNKNOWN_TARGET);
            case TEAM -> teamNames.getOrDefault(ids.get(0), UNKNOWN_TARGET);
            case MANUAL -> MANUAL_LABEL.formatted(new HashSet<>(ids).size());
        };
    }

    // Preview rows are computed, not stored, so one page is cut from the full list
    private <T>PagedResponse<T> slice(List<T> rows, Pageable pageable) {
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        List<T> content = rows.subList(from, to);
        return PagedResponse.of(new PageImpl<>(content, pageable, rows.size()), content);
    }

    private Set<UUID> idsOf(List<UpdateRequest> requests, Function<UpdateRequest, UUID> getter) {
        return requests.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private boolean violates(DataIntegrityViolationException e, String indexName) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message != null && message.contains(indexName);
    }

    // Runs once the surrounding transaction commits, never on rollback
    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

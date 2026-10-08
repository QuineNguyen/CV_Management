package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.cvs.AnchoredNoteRequest;
import com.training.cvmanagementbe.dto.request.cvs.CreateSingleUpdateRequest;
import com.training.cvmanagementbe.dto.response.configs.PagedResponse;
import com.training.cvmanagementbe.dto.response.cvs.AnchoredNoteResponse;
import com.training.cvmanagementbe.dto.response.cvs.CreateUpdateRequestResponse;
import com.training.cvmanagementbe.dto.response.cvs.SkippedLanguageResponse;
import com.training.cvmanagementbe.dto.response.cvs.UpdateRequestResponse;
import com.training.cvmanagementbe.entity.models.*;
import com.training.cvmanagementbe.enums.configs.Action;
import com.training.cvmanagementbe.enums.configs.ErrorCode;
import com.training.cvmanagementbe.enums.configs.TargetType;
import com.training.cvmanagementbe.enums.cvs.Language;
import com.training.cvmanagementbe.enums.cvs.LifecycleStatus;
import com.training.cvmanagementbe.enums.users.RequestStatus;
import com.training.cvmanagementbe.enums.users.Role;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.record.cvs.AnchoredNote;
import com.training.cvmanagementbe.record.cvs.CvContent;
import com.training.cvmanagementbe.record.cvs.UpdateRequestCriteria;
import com.training.cvmanagementbe.record.events.CvUpdateRequestedEvent;
import com.training.cvmanagementbe.repository.*;
import com.training.cvmanagementbe.repository.specifications.UpdateRequestSpecifications;
import com.training.cvmanagementbe.service.UpdateRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UpdateRequestServiceImpl implements UpdateRequestService {

    // Deadlines are calendar days in Vietnam, whatever zone the server runs in.
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Not LocalTime.MAX: DATETIME keeps no fraction and would round .999 up to the next day
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    // V6 unique index on uk_pending_key.
    private static final String PENDING_SLOT_INDEX = "uk_update_requests_pending";

    private final UpdateRequestRepository updateRequestRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final CvProfileRepository cvProfileRepository;
    private final CvRepository cvRepository;
    private final CvVersionRepository cvVersionRepository;
    private final CvContentCodec codec;
    private final AnchoredNoteCodec anchoredNoteCodec;
    private final AnchoredNoteValidator anchoredNoteValidator;
    private final UpdateRequestCanceller updateRequestCanceller;
    private final AuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- Commands ----------

    @Override
    @Transactional
    public CreateUpdateRequestResponse create(CreateSingleUpdateRequest request) {
        // Only Admin/HR create requests; a tech lead does not
        requireAdminOrHr();
        requireNotSelf(request.employeeId());

        // Shared validation, run once whatever the language
        User employee = requireActiveEmployee(request.employeeId());
        LocalDateTime deadline = toDeadline(request.deadline());
        CvProfile profile = request.profileId() == null
                ? null
                : requireProfileOf(request.profileId(), employee.getId());
        List<AnchoredNote> notes = normaliseNotes(request.anchoredNotes());

        // Notes point into one CV's content, so they cannot span several languages
        if (!notes.isEmpty() && !request.language().isSingle()) {
            throw new ApiException.BusinessRuleException(ErrorCode.NOTES_NEED_SINGLE_LANGUAGE);
        }

        // ALL resolves to VI, EN, JA
        List<Language> languages = request.language().toLanguages();
        Map<Language, Cv> activeCvs = activeCvsOf(profile);

        // Anchors only exist for single language + existing CV
        if (!notes.isEmpty()) {
            requireAnchorsExist(activeCvs.get(languages.get(0)), notes);
        }

        Set<Language> alreadyPending = EnumSet.noneOf(Language.class);
        alreadyPending.addAll(updateRequestRepository.findLanguagesByStatus(
                employee.getId(), request.profileId(), RequestStatus.PENDING
        ));

        // One request per language; a taken slot is skipped, never overwritten
        List<UpdateRequest> toCreate = new ArrayList<>();
        List<SkippedLanguageResponse> skipped = new ArrayList<>();
        for (Language language : languages) {
            if (alreadyPending.contains(language)) {
                skipped.add(new SkippedLanguageResponse(language, ErrorCode.PENDING_REQUEST_EXISTS.message()));
                continue;
            }
            toCreate.add(newRequest(request, employee.getId(), language, activeCvs.get(language), deadline, notes));
        }

        // A single taken language, or ALL with every slot taken: nothing to create
        if (toCreate.isEmpty()) {
            throw new ApiException.BusinessRuleException(ErrorCode.PENDING_REQUEST_EXISTS);
        }

        List<UpdateRequest> saved = saveGuarded(toCreate);
        List<UpdateRequestResponse> created = toResponses(saved);

        created.forEach(response -> auditLogger.record(
                Action.CREATE_UPDATE_REQUEST, TargetType.UPDATE_REQUEST, response.id(), null, response
        ));
        saved.forEach(this::publishRequested);

        // Created rows plus the languages that were skipped
        return new CreateUpdateRequestResponse(created, skipped);
    }

    /*
     * Scope is checked before status, so a caller outside it learns nothing
     * about the request. The employee's draft is left alone; only the reminders stop.
     */
    @Override
    @Transactional
    public UpdateRequestResponse cancel(UUID requestId) {
        UpdateRequest request = updateRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException.NotFoundException("update request", requestId));

        Role role = CurrentActor.requireRole();
        UUID actorId = CurrentActor.requireUserId();
        if (!mayCancel(request, role, actorId)) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        UpdateRequestResponse before = toResponses(List.of(request)).get(0);

        // CAS on PENDING: A concurrent cancel or the employee's publish got there first
        if (!updateRequestCanceller.cancelOne(request, actorId, LocalDateTime.now())) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        UpdateRequest cancelled = updateRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException.NotFoundException("update request", requestId));
        UpdateRequestResponse after = toResponses(List.of(cancelled)).get(0);

        auditLogger.record(Action.CANCEL_UPDATE_REQUEST, TargetType.UPDATE_REQUEST, requestId, before, after);
        return after;
    }

    // ---------- Queries ----------

    @Override
    @SuppressWarnings("unchecked")
    public PagedResponse<UpdateRequestResponse> search(UpdateRequestCriteria criteria, Pageable pageable) {
        Specification<UpdateRequest> spec = Specification.allOf(
                UpdateRequestSpecifications.employeeIn(visibleEmployeeIds()),
                UpdateRequestSpecifications.hasStatus(criteria.status()),
                UpdateRequestSpecifications.hasLanguage(criteria.language()),
                UpdateRequestSpecifications.inDepartment(criteria.departmentId()),
                UpdateRequestSpecifications.createdFrom(criteria.fromDate()),
                UpdateRequestSpecifications.createdTo(criteria.toDate()),
                UpdateRequestSpecifications.inBatch(criteria.batchId())
        );

        Page<UpdateRequest> page = updateRequestRepository.findAll(spec, pageable);
        return PagedResponse.of(page, toResponses(page.getContent()));
    }

    // ---------- Validation ----------

    private User requireActiveEmployee(UUID employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException.NotFoundException("user", employeeId));

        // An inactive account cannot act on a request; deactivation cancels them anyway
        if (!employee.isActive()) {
            throw new ApiException.BusinessRuleException(ErrorCode.EMPLOYEE_NOT_ACTIVE);
        }
        return employee;
    }

    // Today is allowed and means 23:59:59 today
    private LocalDateTime toDeadline(LocalDate date) {
        if (date.isBefore(LocalDate.now(BUSINESS_ZONE))) {
            throw new ApiException.BusinessRuleException(ErrorCode.DEADLINE_IN_PAST);
        }
        return date.atTime(END_OF_DAY);
    }

    private CvProfile requireProfileOf(UUID profileId, UUID employeeId) {
        CvProfile profile = cvProfileRepository.findByIdAndLifecycleStatus(profileId, LifecycleStatus.ACTIVE)
                .orElseThrow(() -> new ApiException.NotFoundException("cv profile", profileId));

        if (!profile.getEmployeeId().equals(employeeId)) {
            throw new ApiException.BusinessRuleException(ErrorCode.PROFILE_NOT_OF_EMPLOYEE);
        }
        return profile;
    }

    // Notes point into the current published version of an existing CV
    private void requireAnchorsExist(Cv cv, List<AnchoredNote> notes) {
        CvContent content = cv == null
                ? null
                : cvVersionRepository.findTopByCvIdOrderByVersionNumberDesc(cv.getId())
                .map(version -> codec.read(version.getContentJson()))
                .orElse(null);

        if (content == null) {
            throw new ApiException.BusinessRuleException(ErrorCode.NOTES_NEED_EXISTING_CV);
        }
        anchoredNoteValidator.requireValid(content, notes);
    }

    private void requireAdminOrHr() {
        Role role = CurrentActor.requireRole();
        if (role != Role.ADMIN && role != Role.HR) {
            throw new ApiException.ForbiddenException(ErrorCode.OUT_OF_SCOPE);
        }
    }

    // An Admin/HR owner edits their own CV directly; asking yourself for an update means nothing.
    private void requireNotSelf(UUID employeeId) {
        if (CurrentActor.requireUserId().equals(employeeId)) {
            throw new ApiException.BusinessRuleException(ErrorCode.CANNOT_REQUEST_SELF);
        }
    }

    // ---------- Scope ----------

    /*
     * Admin/HR see every request, a tech lead the members of the teams they lead plus
     * themselves, an employee only requests addressed to them. Null means unrestricted.
     */
    private Set<UUID> visibleEmployeeIds() {
        Role role = CurrentActor.requireRole();
        if (role == Role.ADMIN || role == Role.HR) {
            return null;
        }

        UUID callerId = CurrentActor.requireUserId();
        Set<UUID> ids = new HashSet<>();
        ids.add(callerId);

        if (role == Role.TECH_LEAD) {
            List<UUID> teamIds = teamRepository.findByTechLeadId(callerId).stream().map(Team::getId).toList();
            // An empty IN clause is invalid SQL, so the lookup is skipped when nothing is led
            if (!teamIds.isEmpty()) {
                ids.addAll(userRepository.findUserIdsByTeamIds(teamIds));
            }
        }
        return ids;
    }

    // Admin cancels any request, HR only their own - HR peers do not oversee each other
    private boolean mayCancel(UpdateRequest request, Role role, UUID callerId) {
        return role == Role.ADMIN || (role == Role.HR && callerId.equals(request.getCreatedBy()));
    }

    // ---------- Writing ----------

    private UpdateRequest newRequest(CreateSingleUpdateRequest request, UUID employeeId, Language language,
                                     Cv activeCv, LocalDateTime deadline, List<AnchoredNote> notes) {
        UpdateRequest entity = new UpdateRequest();
        entity.setEmployeeId(employeeId);
        entity.setProfileId(request.profileId());
        entity.setLanguage(language);
        // Only an ACTIVE CV is linked; a deleted one holds no slot.
        entity.setCvId(activeCv == null ? null : activeCv.getId());
        entity.setReason(request.reason().trim());
        entity.setAnchoredNotes(anchoredNoteCodec.write(notes));
        entity.setDeadline(deadline);
        entity.setStatus(RequestStatus.PENDING);
        return entity;
    }

    /*
     * The lookup above only decides what to report.
     * A concurrent create slipping in between makes this whole call a conflict, so nothing
     * is half-written. Catching per language would not work - the failed flush marks the whole
     * transaction rollback-only.
     */
    private List<UpdateRequest> saveGuarded(List<UpdateRequest> requests) {
        try {
            return updateRequestRepository.saveAllAndFlush(requests);
        } catch (DataIntegrityViolationException e) {
            if (violates(e, PENDING_SLOT_INDEX)) {
                throw new ApiException.BusinessRuleException(ErrorCode.PENDING_REQUEST_EXISTS);
            }
            throw e;
        }
    }

    private boolean violates(DataIntegrityViolationException e, String indexName) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        return message != null && message.contains(indexName);
    }

    // Delivered after commit by NotificationListener
    private void publishRequested(UpdateRequest request) {
        eventPublisher.publishEvent(new CvUpdateRequestedEvent(
                request.getId(),
                request.getEmployeeId(),
                request.getCvId(),
                request.getProfileId(),
                request.getLanguage(),
                request.getReason()
        ));
    }

    // ---------- Private helpers ----------

    private Map<Language, Cv> activeCvsOf(CvProfile profile) {
        if (profile == null) {
            return Map.of();
        }
        return cvRepository
                .findByProfileIdAndLifecycleStatusOrderByLanguageAsc(profile.getId(), LifecycleStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(Cv::getLanguage, Function.identity(),
                        (first, second) -> first, () -> new EnumMap<>(Language.class)));
    }

    // Blank anchor parts mean "none"; the note text is trimmed
    private List<AnchoredNote> normaliseNotes(List<AnchoredNoteRequest> notes) {
        if (notes == null) {
            return List.of();
        }
        return notes.stream()
                .map(note -> new AnchoredNote(
                        note.sectionKey(),
                        blankToNull(note.itemId()),
                        blankToNull(note.fieldKey()),
                        note.note().trim()
                ))
                .toList();
    }

    // Names resolved in bulk, one query per page instead of one per row
    private List<UpdateRequestResponse> toResponses(List<UpdateRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        Set<UUID> userIds = new HashSet<>();
        requests.forEach(request -> {
            userIds.add(request.getEmployeeId());
            if (request.getCreatedBy() != null) {
                userIds.add(request.getCreatedBy());
            }
        });
        Map<UUID, String> userNames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        Set<UUID> profileIds = requests.stream()
                .map(UpdateRequest::getProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> profileNames = profileIds.isEmpty()
                ? Map.of()
                : cvProfileRepository.findAllById(profileIds).stream()
                .collect(Collectors.toMap(CvProfile::getId, CvProfile::getName));

        // Resolved once per page: Which rows offer a cancel action to this caller
        Role role = CurrentActor.requireRole();
        UUID callerId = CurrentActor.requireUserId();

        return requests.stream()
                .map(request -> toResponse(request, userNames, profileNames,
                        request.getStatus() == RequestStatus.PENDING && mayCancel(request, role, callerId)))
                .toList();
    }

    private UpdateRequestResponse toResponse(UpdateRequest request,
                                             Map<UUID, String> userNames,
                                             Map<UUID, String> profileNames,
                                             boolean cancellable) {
        List<AnchoredNoteResponse> notes = anchoredNoteCodec.read(request.getAnchoredNotes()).stream()
                .map(note -> new AnchoredNoteResponse(note.sectionKey(), note.itemId(), note.fieldKey(), note.note()))
                .toList();

        return new UpdateRequestResponse(
                request.getId(),
                request.getEmployeeId(),
                userNames.get(request.getEmployeeId()),
                request.getProfileId(),
                request.getProfileId() == null ? null : profileNames.get(request.getProfileId()),
                request.getCvId(),
                request.getLanguage(),
                request.getReason(),
                request.getDeadline(),
                request.getStatus(),
                notes,
                request.getCreatedBy() == null ? null : userNames.get(request.getCreatedBy()),
                request.getCreatedAt(),
                cancellable,
                request.getBatchRequestId()
        );
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.ProfileUpdateSubmitRequest;
import com.training.cvmanagementbe.dto.request.RejectProfileUpdateRequest;
import com.training.cvmanagementbe.dto.response.PagedResponse;
import com.training.cvmanagementbe.dto.response.ProfileUpdateRequestResponse;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.ProfileUpdateRequest;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.*;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.ImageFileRepository;
import com.training.cvmanagementbe.repository.ProfileUpdateRequestRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.ProfileUpdateRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * The approval workflow for self-service profile edits.
 *
 * - The users table is written exactly once, on approve. Everything before that lives on the
 * request row, which is why a rejected request leaves no trace on the user and why a PENDING one
 * can be cancelled by hard delete.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileUpdateRequestServiceImpl implements ProfileUpdateRequestService {

    private final ProfileUpdateRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ImageFileRepository imageFileRepository;
    private final AvatarUrlResolver avatarUrlResolver;
    private final AuditLogger auditLogger;

    // ---------- Self-service ----------

    @Override
    @Transactional
    public ProfileUpdateRequestResponse submit(UUID userId, ProfileUpdateSubmitRequest request) {
        User user = requireUser(userId);

        /*
         * No "does a PENDING one already exist?" lookup here on purpose. The pending_slot
         * unique index is the enforcement; a check in front of it would be a read-then-write that
         * two concurrent submits pass together, and it would read as the real rule while being
         * only an optimisation. The constraint violation is translated to
         * PROFILE_UPDATE_PENDING_EXISTS (409) by the DbConstraint mapping.
         */
        ProfileUpdateRequest saved = requestRepository.saveAndFlush(buildRequest(user, request));

        // TODO (Phase 4): notify the reviewers this request routes to, once the mail module lands.
        // Exactly that set and no wider - HR is not mailed about an HR or Admin request,
        auditLogger.record(Action.SUBMIT_PROFILE_UPDATE, TargetType.PROFILE_UPDATE_REQUEST,
                saved.getId(), null, saved.getStatus());

        return toResponse(saved, user, Map.of());
    }

    @Override
    public ProfileUpdateRequestResponse getLatest(UUID userId) {
        User user = requireUser(userId);

        return requestRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(request -> toResponse(request, user, reviewerNames(List.of(request))))
                .orElse(null);
    }

    @Override
    @Transactional
    public void withdrawPending(UUID userId) {
        ProfileUpdateRequest pending = requestRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(ProfileUpdateRequest::isPending)
                .orElseThrow(() -> new ApiException.BusinessRuleException(
                        ErrorCode.PROFILE_UPDATE_NO_PENDING_REQUEST
                ));

        /*
         * reviewedBy stays null: nobody reviewed it. reject_reason stays null too - someone
         * dropping their own unseen proposal owes no explanation to anyone, symmetric with
         * cv_drafts.cancellation_reason being required only on the Admin path.
         *
         * Same compare-and-set as approve/reject, because this is the same race seen from the
         * requester's side: withdrawing at the exact moment a reviewer decides.
         */
        int updated = requestRepository.closeIfPending(
                pending.getId(), ProfileUpdateStatus.CANCELLED, null, null,
                LocalDateTime.now(), CurrentActor.requireUserId(), LocalDateTime.now()
        );

        if (updated == 0) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }

        auditLogger.record(Action.CANCEL_PROFILE_UPDATE, TargetType.PROFILE_UPDATE_REQUEST,
                pending.getId(), pending.getStatus(), null);
    }

    // ---------- Reviewer side ----------

    @Override
    public PagedResponse<ProfileUpdateRequestResponse> list(ProfileUpdateStatus status, Pageable pageable) {
        Page<ProfileUpdateRequest> page = requestRepository.searchInScope(
                status, reviewableRequesterRoles(), pageable
        );

        return PagedResponse.of(page, toResponses(page.getContent()));
    }

    @Override
    public ProfileUpdateRequestResponse getById(UUID id) {
        ProfileUpdateRequest request = requireRequest(id);
        return toResponse(request, requireUser(request.getUserId()), reviewerNames(List.of(request)));
    }

    @Override
    @Transactional
    public ProfileUpdateRequestResponse approve(UUID id) {
        ProfileUpdateRequest request = requireInScope(id);
        requirePending(request);

        // Claim first, write the user second. Whoever wins the compare-and-set is the only reviewer that
        // reaches the users table at all - the loser's transaction never gets past claim().
        claim(id, ProfileUpdateStatus.APPROVED, null);

        // Re-read: the bulk update cleared the persistence context.
        ProfileUpdateRequest closed = requireRequest(id);
        User user = requireUser(closed.getUserId());

        applyIfPresent(closed.getRequestedFullName(), user::setFullName);
        applyIfPresent(closed.getRequestedPhoneNumber(), user::setPhoneNumber);
        applyIfPresent(closed.getRequestedAddress(), user::setAddress);
        if (closed.getRequestedDateOfBirth() != null) {
            user.setDateOfBirth(closed.getRequestedDateOfBirth());
        }
        if (closed.getRequestedAvatarImageId() != null) {
            user.setAvatarImageId(closed.getRequestedAvatarImageId());
        }
        // No token revocation: role and status are not in scope here.
        // No CV is touched either - users only ever seeds Personal info at creation.
        userRepository.save(user);

        auditLogger.record(Action.APPROVE_PROFILE_UPDATE, TargetType.PROFILE_UPDATE_REQUEST,
                closed.getId(), ProfileUpdateStatus.PENDING, closed.getStatus());

        return toResponse(closed, user, reviewerNames(List.of(closed)));
    }

    @Override
    @Transactional
    public ProfileUpdateRequestResponse reject(UUID id, RejectProfileUpdateRequest request) {
        ProfileUpdateRequest entity = requireInScope(id);
        requirePending(entity);

        claim(id, ProfileUpdateStatus.REJECTED, request.reason().trim());
        ProfileUpdateRequest closed = requireRequest(id);

        auditLogger.record(Action.REJECT_PROFILE_UPDATE, TargetType.PROFILE_UPDATE_REQUEST,
                closed.getId(), ProfileUpdateStatus.PENDING, closed.getStatus());

        return toResponse(closed, requireUser(closed.getUserId()), reviewerNames(List.of(closed)));
    }

    @Override
    public long countPending() {
        return requestRepository.countByStatusInScope(ProfileUpdateStatus.PENDING, reviewableRequesterRoles());
    }

    // ---------- Building ----------

    private ProfileUpdateRequest buildRequest(User user, ProfileUpdateSubmitRequest submitted) {
        // Only genuine differences are stored, so approve never rewrites a value with itself.
        String fullName = changedOrNull(trimToNull(submitted.fullName()), user.getFullName());
        String phoneNumber = changedOrNull(trimToNull(submitted.phoneNumber()), user.getPhoneNumber());
        String address = changedOrNull(trimToNull(submitted.address()), user.getAddress());
        LocalDate dateOfBirth = Objects.equals(submitted.dateOfBirth(), user.getDateOfBirth())
                ? null : submitted.dateOfBirth();
        UUID avatarImageId = Objects.equals(submitted.avatarImageId(), user.getAvatarImageId())
                ? null : submitted.avatarImageId();

        if (fullName == null && phoneNumber == null && address == null
                && dateOfBirth == null && avatarImageId == null) {
            throw new ApiException.BusinessRuleException(ErrorCode.PROFILE_UPDATE_NO_CHANGES);
        }
        if (avatarImageId != null && !imageFileRepository.existsById(avatarImageId)) {
            throw new ApiException.NotFoundException("image", avatarImageId);
        }

        ProfileUpdateRequest entity = new ProfileUpdateRequest();
        entity.setUserId(user.getId());
        entity.setStatus(ProfileUpdateStatus.PENDING);
        entity.setRequestedFullName(fullName);
        entity.setRequestedDateOfBirth(dateOfBirth);
        entity.setRequestedPhoneNumber(phoneNumber);
        entity.setRequestedAddress(address);
        entity.setRequestedAvatarImageId(avatarImageId);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    // Page-wide mapping: users, reviewers and avatar URLs are each fetched once.
    private List<ProfileUpdateRequestResponse> toResponses(List<ProfileUpdateRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> userById = userRepository.findAllById(requests.stream()
                .map(ProfileUpdateRequest::getUserId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, String> reviewerNames = reviewerNames(requests);

        return requests.stream()
                .map(request -> toResponse(request, userById.get(request.getUserId()), reviewerNames))
                .toList();
    }

    private Map<UUID, String> reviewerNames(List<ProfileUpdateRequest> requests) {
        Set<UUID> reviewerIds = requests.stream()
                .map(ProfileUpdateRequest::getReviewedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return reviewerIds.isEmpty() ? Map.of() : userRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private ProfileUpdateRequestResponse toResponse(ProfileUpdateRequest request,
                                                    User user,
                                                    Map<UUID, String> reviewerNames) {
        Map<UUID, String> avatarUrls = avatarUrlResolver.resolveAll(
                Arrays.asList(user == null ? null : user.getAvatarImageId(),
                        request.getRequestedAvatarImageId()));

        return new ProfileUpdateRequestResponse(
                request.getId(),
                request.getUserId(),
                user == null ? null : user.getFullName(),
                user == null ? null : user.getEmail(),
                request.getStatus(),

                user == null ? null : user.getFullName(),
                user == null ? null : user.getDateOfBirth(),
                user == null ? null : user.getPhoneNumber(),
                user == null ? null : user.getAddress(),
                user == null ? null : user.getAvatarImageId(),
                user == null ? null : avatarUrls.get(user.getAvatarImageId()),

                request.getRequestedFullName(),
                request.getRequestedDateOfBirth(),
                request.getRequestedPhoneNumber(),
                request.getRequestedAddress(),
                request.getRequestedAvatarImageId(),
                avatarUrls.get(request.getRequestedAvatarImageId()),

                reviewerNames.get(request.getReviewedBy()),
                request.getReviewedAt(),
                request.getRejectReason(),
                request.getCreatedAt()
        );
    }

    // ---------- Helpers ----------

    /*
     * Answers "has this already been decided?" for the ordinary case of a reviewer working from a
     * stale list. The compare-and-set in claim() covers the narrower case where it was PENDING when this
     * transaction read it and is not anymore - those two deserve different messages, which is
     * why both exist.
     */
    private void requirePending(ProfileUpdateRequest request) {
        if (!request.isPending()) {
            throw new ApiException.BusinessRuleException(ErrorCode.PROFILE_UPDATE_NOT_PENDING);
        }
    }

    private ProfileUpdateRequest requireRequest(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFoundException("profile update request", id));
    }

    private void claim(UUID id, ProfileUpdateStatus outcome, String rejectReason) {
        UUID reviewerId = CurrentActor.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        int updated = requestRepository.closeIfPending(
                id, outcome, rejectReason, reviewerId, now, reviewerId, now
        );

        if (updated == 0) {
            throw new ApiException.ConflictException(ErrorCode.STALE_STATE);
        }
    }

    // No ADMIN request ever exists, so this doubles as "reviewer is never the requester".
    private Set<Role> reviewableRequesterRoles() {
        return CurrentActor.requireRole().manageableRoles();
    }

    /*
     * Resolves a request the caller is allowed to see at all.
     *
     * - 404, not 403: an HR opening an Admin's request by URL must not learn that the row
     * exists. The list already hides it; this makes the direct link behave the same way, which is
     * the difference between "filtered list" and "access check".
     */
    private ProfileUpdateRequest requireInScope(UUID id) {
        ProfileUpdateRequest request = requireRequest(id);
        Role requesterRole = requireUser(request.getUserId()).getRole();

        if (!reviewableRequesterRoles().contains(requesterRole)) {
            throw new ApiException.NotFoundException("profile update request", id);
        }
        return request;
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException.NotFoundException("user", id));
    }

    private void applyIfPresent(String value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private String changedOrNull(String submitted, String current) {
        return (submitted == null || submitted.equals(current)) ? null : submitted;
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

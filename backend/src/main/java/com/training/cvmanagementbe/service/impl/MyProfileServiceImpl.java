package com.training.cvmanagementbe.service.impl;

import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.dto.request.ProfileUpdateSubmitRequest;
import com.training.cvmanagementbe.dto.response.MyProfileResponse;
import com.training.cvmanagementbe.dto.response.UserResponse;
import com.training.cvmanagementbe.entity.models.Department;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.Action;
import com.training.cvmanagementbe.enums.ErrorCode;
import com.training.cvmanagementbe.enums.TargetType;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.DepartmentRepository;
import com.training.cvmanagementbe.repository.ImageFileRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import com.training.cvmanagementbe.service.MyProfileService;
import com.training.cvmanagementbe.service.ProfileUpdateRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyProfileServiceImpl implements MyProfileService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ImageFileRepository imageFileRepository;
    private final AvatarUrlResolver avatarUrlResolver;
    private final ProfileUpdateRequestService profileUpdateRequestService;
    private final AuditLogger auditLogger;

    @Override
    public MyProfileResponse getMyProfile(UUID userId) {
        User user = requireUser(userId);

        Department department = user.getPrimaryDepartmentId() == null
                ? null
                : departmentRepository.findById(user.getPrimaryDepartmentId()).orElse(null);

        /*
         * An Admin normally has no request at all, but this still reads the latest one: an account
         * promoted to Admin keeps whatever it submitted beforehand and hiding that history would
         * leave a rejected request unanswered on screen.
         */
        return new MyProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                department == null ? null : department.getCode(),
                department == null ? null : department.getName(),
                user.getDateOfBirth(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getAvatarImageId(),
                avatarUrlResolver.resolve(user.getAvatarImageId()),
                user.getRole().requiresProfileUpdateApproval(),
                profileUpdateRequestService.getLatest(userId)
        );
    }

    @Override
    @Transactional
    public MyProfileResponse saveMyProfile(UUID userId, ProfileUpdateSubmitRequest request) {
        /*
         * The role is read from the record being changed, not from CurrentActor. They are always
         * the same person here - the controller passes the authenticated id and there is no
         * submit-on-behalf-of path - and reading the row makes that independent of how the caller
         * got here.
         */
        User user = requireUser(userId);

        if (user.getRole().requiresProfileUpdateApproval()) {
            profileUpdateRequestService.submit(userId, request);
        } else {
            writeDirectly(user, request);
        }
        return getMyProfile(userId);
    }

    /*
     * Admin path: the values land on the record immediately, with no request row to look
     * at afterward, so the audit entry is the entire record of what changed.
     *
     * - Only role and status trigger token revocation and neither is in scope here, so
     * an Admin editing their own name never signs themselves out.
     */
    private void writeDirectly(User user, ProfileUpdateSubmitRequest request) {
        // Resolved before anything is written, so "nothing changed" is refused rather than
        // recorded as an audit entry with an empty diff.
        String fullName = changedOrNull(trimToNull(request.fullName()), user.getFullName());
        String phoneNumber = changedOrNull(trimToNull(request.phoneNumber()), user.getPhoneNumber());
        String address = changedOrNull(trimToNull(request.address()), user.getAddress());
        LocalDate dateOfBirth = Objects.equals(request.dateOfBirth(), user.getDateOfBirth())
                ? null : request.dateOfBirth();
        UUID avatarImageId = Objects.equals(request.avatarImageId(), user.getAvatarImageId())
                ? null : request.avatarImageId();

        if (fullName == null && phoneNumber == null && address == null
                && dateOfBirth == null && avatarImageId == null) {
            throw new ApiException.BusinessRuleException(ErrorCode.PROFILE_UPDATE_NO_CHANGES);
        }

        UserResponse before = snapshot(user);

        // An empty field means "keep the current value"; this path cannot clear one.
        applyIfPresent(fullName, user::setFullName);
        applyIfPresent(phoneNumber, user::setPhoneNumber);
        applyIfPresent(address, user::setAddress);
        if (dateOfBirth != null) {
            user.setDateOfBirth(dateOfBirth);
        }
        if (avatarImageId != null) {
            requireImageExists(avatarImageId);
            user.setAvatarImageId(avatarImageId);
        }

        User saved = userRepository.save(user);
        auditLogger.record(Action.UPDATE_USER, TargetType.USER, user.getId(), before, snapshot(saved));

        // No notification on this path: the Admin is both the actor and the subject, so there is
        // nobody left to tell. Worth stating, since every other write here does notify.
    }

    // ---------- Helpers ----------

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException.NotFoundException("user", userId));
    }

    // The FK would catch this at flush time, but as a constraint violation with no useful code.
    private void requireImageExists(UUID imageId) {
        if (!imageFileRepository.existsById(imageId)) {
            throw new ApiException.NotFoundException("image", imageId);
        }
    }

    /*
     * Audit snapshot, deliberately flat - same shape UserServiceImpl writes for TargetType.USER,
     * so a diff on a user record reads the same whichever path produced it.
     *
     * Joined names, teams and the presigned URL are left out: they cost extra queries, none of
     * them can change on this path and a signed URL expires anyway.
     */
    private UserResponse snapshot(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getStatus(),
                user.getPrimaryDepartmentId(),
                null,
                null,
                user.getDateOfBirth(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getAvatarImageId(),
                null,
                List.of(),
                List.of(),
                user.isMustChangePassword(),
                user.getCreatedAt()
        );
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

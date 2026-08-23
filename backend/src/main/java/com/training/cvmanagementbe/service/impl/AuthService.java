package com.training.cvmanagementbe.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.training.cvmanagementbe.common.AuditLogger;
import com.training.cvmanagementbe.config.auth.GoogleTokenVerifier;
import com.training.cvmanagementbe.config.auth.PasswordGenerator;
import com.training.cvmanagementbe.dto.request.ChangePasswordRequest;
import com.training.cvmanagementbe.dto.request.GoogleLoginRequest;
import com.training.cvmanagementbe.dto.request.LoginRequest;
import com.training.cvmanagementbe.dto.response.AuthenticatedUser;
import com.training.cvmanagementbe.dto.response.LoginResponse;
import com.training.cvmanagementbe.dto.response.ResetPasswordResponse;
import com.training.cvmanagementbe.entity.models.CurrentActor;
import com.training.cvmanagementbe.entity.models.ExternalAccountLink;
import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.*;
import com.training.cvmanagementbe.exception.ApiException;
import com.training.cvmanagementbe.repository.ExternalAccountLinkRepository;
import com.training.cvmanagementbe.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class AuthService {

    private static final String USER_ENTITY = "user";

    private final UserRepository userRepository;
    private final ExternalAccountLinkRepository linkRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogger auditLogger;
    private final PasswordGenerator passwordGenerator;
    private final GoogleTokenVerifier googleTokenVerifier;

    private final int maxFailedAttempts;
    private final long lockDurationMinutes;
    private final int minPasswordLength;

    public AuthService(UserRepository userRepository,
                       ExternalAccountLinkRepository linkRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditLogger auditLogger,
                       PasswordGenerator passwordGenerator,
                       GoogleTokenVerifier googleTokenVerifier,
                       @Value("${security.login.max-failed-attempts:5}") int maxFailedAttempts,
                       @Value("${security.login.lock-duration-minutes:5}") long lockDurationMinutes,
                       @Value("${security.password.min-length:8}") int minPasswordLength) {
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogger = auditLogger;
        this.passwordGenerator = passwordGenerator;
        this.googleTokenVerifier = googleTokenVerifier;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationMinutes = lockDurationMinutes;
        this.minPasswordLength = minPasswordLength;
    }

    // ---------- Password sign-in ----------
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ApiException.UnauthorizedException(ErrorCode.INVALID_CREDENTIALS));

        // Sign-in writes to `users` (lock counter, revocation mark) before the caller is
        // authenticated, so the actor must be declared for JPA auditing and AuditLogger.
        return asActor(user, () -> {
            requireActive(user);

            if (checkLock(user) == LockStatus.LOCKED) {
                throw new ApiException.AccountLockedException(remainingLockSeconds(user));
            }

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                recordFailedAttempt(user);
                // If this attempt just triggered the lock, tell the client immediately.
                ErrorCode credential = user.getLockedUntil() != null
                        ? ErrorCode.ACCOUNT_LOCKED
                        : ErrorCode.INVALID_CREDENTIALS;
                throw new ApiException.UnauthorizedException(credential);
            }

            recordSuccess(user);
            // mustChangePassword users still get a token; the frontend routes them to the change screen.
            return issueToken(user);
        });
    }

    // ---------- Google sign-in ----------
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.idToken());
        String email = payload.getEmail();
        String providerUserId = payload.getSubject();

        // The system never creates a user from the Google path
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException.UnauthorizedException(ErrorCode.GOOGLE_EMAIL_NOT_REGISTERED));

        return asActor(user, () -> {
            requireActive(user);
            linkOrVerifyGoogleAccount(user, providerUserId, email);

            // The Google path bypasses the failed-login counter, but clears any leftover lock.
            recordSuccess(user);
            return issueToken(user);
        });
    }

    private void linkOrVerifyGoogleAccount(User user, String providerUserId, String email) {
        linkRepository.findByUserId(user.getId()).ifPresentOrElse(
                link -> {
                    if (!link.getProviderUserId().equals(providerUserId)) {
                        throw new ApiException.UnauthorizedException(ErrorCode.GOOGLE_ACCOUNT_MISMATCH);
                    }
                },
                () -> createLink(user, providerUserId, email)
        );
    }

    // First Google sign-in creates the link automatically
    private void createLink(User user, String providerUserId, String email) {
        linkRepository.findByProviderUserId(providerUserId).ifPresent(existing -> {
            throw new ApiException.UnauthorizedException(ErrorCode.GOOGLE_ACCOUNT_MISMATCH);
        });

        ExternalAccountLink link = new ExternalAccountLink();
        link.setUserId(user.getId());
        link.setProviderUserId(providerUserId);
        link.setProviderEmail(email);
        link.setLinkedAt(LocalDateTime.now());
        linkRepository.save(link);

        auditLogger.record(Action.LINK_EXTERNAL_ACCOUNT,
                TargetType.USER, user.getId(), null, email);
    }

    // ---------- Sign-out ----------
    @Transactional
    public void logout(UUID currentUserId) {
        User user = requireUser(currentUserId);
        user.setTokenValidFrom(jwtService.revocationMark());
        userRepository.save(user);

        auditLogger.record(Action.LOGOUT,
                TargetType.USER, user.getId(), null, null);
    }

    // --------- Change own password ----------
    @Transactional
    public void changePassword(ChangePasswordRequest request, UUID currentUserId) {
        User user = requireUser(currentUserId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException.BusinessRuleException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException.BusinessRuleException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException.BusinessRuleException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }
        validatePasswordStrength(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setTokenValidFrom(jwtService.revocationMark());
        userRepository.save(user);

        // Audit records the event only - never the password itself.
        auditLogger.record(Action.CHANGE_PASSWORD,
                TargetType.USER, user.getId(), null, null);
    }

    // ---------- Admin resets another user's password ----------
    @Transactional
    public ResetPasswordResponse resetPassword(UUID targetUserId) {
        User target = requireUser(targetUserId);
        if (!target.isActive()) {
            throw new ApiException.BusinessRuleException(ErrorCode.ACCOUNT_INACTIVE);
        }

        String temporaryPassword = passwordGenerator.generate();
        target.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        target.setMustChangePassword(true);
        target.setFailedLoginCount(0);
        target.setLockedUntil(null);
        target.setTokenValidFrom(jwtService.revocationMark());
        userRepository.save(target);

        auditLogger.record(Action.RESET_PASSWORD,
                TargetType.USER, target.getId(), null, target.getUsername());

        // TODO Next stage: publish notification event (email + in-app) to `target`.
        return new ResetPasswordResponse(temporaryPassword);
    }

    // ---------- Brute-force protection ----------
    /*
     * Resetting the counter once locked_until has passed is mandatory:
     * without it the first typo after the lock expires hít the threshold again,
     * producing an endless lock loop.
     */
    private LockStatus checkLock(User user) {
        Instant lockedUntil = user.getLockedUntil();
        if (lockedUntil == null) {
            return LockStatus.NOT_LOCKED;
        }
        if (Instant.now().isBefore(lockedUntil)) {
            return LockStatus.LOCKED;
        }
        recordSuccess(user);
        return LockStatus.NOT_LOCKED;
    }

    private long remainingLockSeconds(User user) {
        return Math.max(1, Duration.between(Instant.now(), user.getLockedUntil()).toSeconds() + 1);
    }

    private void recordFailedAttempt(User user) {
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        if (user.getFailedLoginCount() >= maxFailedAttempts) {
            user.setLockedUntil(Instant.now().plus(lockDurationMinutes, ChronoUnit.MINUTES));
        }
        userRepository.save(user);
    }

    private void recordSuccess(User user) {
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < minPasswordLength) {
            throw new ApiException.BusinessRuleException(ErrorCode.PASSWORD_TOO_WEAK);
        }
        for (PasswordCharset charset : PasswordCharset.values()) {
            if (!charset.isPresentIn(password)) {
                throw new ApiException.BusinessRuleException(ErrorCode.PASSWORD_TOO_WEAK);
            }
        }
    }

    // ---------- Helpers ----------
    // Runs work under an explicit actor. Used on the two paths that write before authentication
    private <T> T asActor(User user, Supplier<T> work) {
        CurrentActor.set(user.getId(), user.getRole());
        try {
            return work.get();
        } finally {
            CurrentActor.clear();
        }
    }

    private LoginResponse issueToken(User user) {
        return new LoginResponse(jwtService.generateToken(user), AuthenticatedUser.from(user));
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new ApiException.UnauthorizedException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException.NotFoundException(USER_ENTITY, userId));
    }
}

package com.training.cvmanagementbe.service;

import com.training.cvmanagementbe.entity.models.User;
import com.training.cvmanagementbe.enums.LockStatus;
import com.training.cvmanagementbe.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// Brute-force protection on the password path only
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;

    @Value("${security.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${security.login.lock-duration-minutes:5}")
    private long lockDurationMinutes;

    /*
     * Returns LOCKED only while the lock window is still open.
     * Resetting the counter once locked_until has passed is mandatory:
     * without it the first typo after the lock expires hits the threshold again,
     * producing an endless lock loop.
     */
    @Transactional
    public LockStatus checkLock(User user) {
        Instant lockedUntil = user.getLockedUntil();
        if (lockedUntil == null) {
            return LockStatus.NOT_LOCKED;
        }
        if (Instant.now().isBefore(lockedUntil)) {
            return LockStatus.LOCKED;
        }
        clearLock(user);
        return LockStatus.NOT_LOCKED;
    }

    @Transactional
    public void recordFailedAttempt(User user) {
        user.setFailedLoginCount(user.getFailedLoginCount() + 1);
        if (user.getFailedLoginCount() >= maxFailedAttempts) {
            user.setLockedUntil(Instant.now().plus(lockDurationMinutes, ChronoUnit.MINUTES));
        }
        userRepository.save(user);
    }

    @Transactional
    public void recordSuccess(User user) {
        clearLock(user);
    }

    private void clearLock(User user) {
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}

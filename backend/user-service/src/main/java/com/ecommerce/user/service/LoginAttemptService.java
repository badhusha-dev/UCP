package com.ecommerce.user.service;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Temporary account lockout after repeated failed sign-ins. A lock expires on its own; a successful sign-in
 * clears the failure count. The counter restarts from zero once a lock has been applied.
 */
@Service
@Slf4j
public class LoginAttemptService {

    private final UserRepository users;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(UserRepository users,
                               @Value("${auth.lockout.max-failed-attempts:5}") int maxFailedAttempts,
                               @Value("${auth.lockout.duration:PT15M}") Duration lockDuration) {
        this.users = users;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = lockDuration;
    }

    public boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    @Transactional
    public void recordFailure(User user) {
        users.incrementFailedLoginAttempts(user.getId());
        Instant until = Instant.now().plus(lockDuration);
        if (users.lockWhenAttemptsReach(user.getId(), maxFailedAttempts, until) > 0) {
            log.warn("User {} locked until {} after {} failed sign-in attempts", user.getId(), until, maxFailedAttempts);
        }
    }

    @Transactional
    public void recordSuccess(User user) {
        users.clearLoginFailures(user.getId());
    }
}

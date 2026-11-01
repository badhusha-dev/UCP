package com.ecommerce.user.repository;

import com.ecommerce.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    long countByRoleIgnoreCase(String role);
    long countByRoleIgnoreCaseAndActiveTrue(String role);
    boolean existsByRoleIgnoreCase(String role);

    // Single-statement updates so concurrent failed sign-ins cannot lose an increment.
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    int incrementFailedLoginAttempts(@Param("id") Long id);

    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = :until, u.failedLoginAttempts = 0 WHERE u.id = :id AND u.failedLoginAttempts >= :maxAttempts")
    int lockWhenAttemptsReach(@Param("id") Long id, @Param("maxAttempts") int maxAttempts, @Param("until") Instant until);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = NULL WHERE u.id = :id AND (u.failedLoginAttempts <> 0 OR u.lockedUntil IS NOT NULL)")
    int clearLoginFailures(@Param("id") Long id);
}

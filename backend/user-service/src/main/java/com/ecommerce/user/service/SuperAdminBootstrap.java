package com.ecommerce.user.service;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    @Value("${SUPER_ADMIN_EMAIL:}")
    private String configuredEmail;

    @Value("${SUPER_ADMIN_PASSWORD:}")
    private String configuredPassword;

    @Value("${SUPER_ADMIN_NAME:Platform Super Admin}")
    private String configuredName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.countByRoleIgnoreCaseAndActiveTrue("SUPER_ADMIN") > 0) return;
        if (configuredEmail == null || configuredEmail.isBlank()) {
            log.warn("No active Super Admin exists. Set SUPER_ADMIN_EMAIL to an active ADMIN account to bootstrap the root role.");
            return;
        }
        String email = configuredEmail.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            if (!validBootstrapPassword()) {
                log.error("Super Admin bootstrap skipped: set SUPER_ADMIN_PASSWORD (at least 12 characters) to create the initial account.");
                return;
            }
            user = new User();
            user.setEmail(email);
            user.setName(configuredName == null || configuredName.isBlank() ? "Platform Super Admin" : configuredName.trim());
            user.setPassword(passwordEncoder.encode(configuredPassword));
            user.setActive(true);
            user.setRole("SUPER_ADMIN");
            user = users.save(user);
        } else if ("ADMIN".equalsIgnoreCase(user.getRole()) && user.isActive()) {
            user.setRole("SUPER_ADMIN");
            user = users.save(user);
        } else if ("ADMIN".equalsIgnoreCase(user.getRole()) && !user.isActive() && validBootstrapPassword()) {
            user.setPassword(passwordEncoder.encode(configuredPassword));
            user.setActive(true);
            user.setName(configuredName == null || configuredName.isBlank() ? user.getName() : configuredName.trim());
            user.setRole("SUPER_ADMIN");
            user = users.save(user);
        } else if ("SUPER_ADMIN".equalsIgnoreCase(user.getRole()) && !user.isActive() && validBootstrapPassword()) {
            user.setPassword(passwordEncoder.encode(configuredPassword));
            user.setActive(true);
            user = users.save(user);
        } else {
            log.error("Super Admin bootstrap skipped: the email is already assigned to a non-admin account.");
            return;
        }
        jdbc.update("INSERT INTO access_audit_log(actor_user_id,actor_email,action,target_type,target_id,after_value) VALUES(?,?,?, ?, ?, ?::jsonb)",
                user.getId(), user.getEmail(), "RBAC_BOOTSTRAP", "ROLE", "SUPER_ADMIN", "{\"role\":\"SUPER_ADMIN\"}");
        log.info("Bootstrapped Super Admin role for configured administrator account.");
    }

    private boolean validBootstrapPassword() {
        return configuredPassword != null && configuredPassword.length() >= 12;
    }
}

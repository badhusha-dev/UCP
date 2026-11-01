package com.ecommerce.qa.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QaPermissionService {
    public Actor require(String permission, String userId, String email, String roleHeader,
                         String legacyRoleHeader, String permissionsHeader) {
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user context is required");
        }
        String roles = roleHeader == null || roleHeader.isBlank() ? legacyRoleHeader : roleHeader;
        Set<String> roleSet = split(roles).stream().map(QaPermissionService::normalizeRole).collect(Collectors.toSet());
        Set<String> grants = split(permissionsHeader).stream().collect(Collectors.toSet());
        if (!roleSet.contains("SUPER_ADMIN") && !grants.contains("*") && !grants.contains(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission " + permission + " is required");
        }
        try {
            return new Actor(Long.parseLong(userId), email == null || email.isBlank() ? "unknown" : email,
                    roleSet.stream().sorted().findFirst().orElse("UNKNOWN"), grants);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user id is invalid");
        }
    }

    private static Set<String> split(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeRole(String value) {
        return value == null ? "" : value.trim().toUpperCase().replaceFirst("^ROLE_", "");
    }

    public record Actor(long id, String email, String role, Set<String> permissions) {}
}

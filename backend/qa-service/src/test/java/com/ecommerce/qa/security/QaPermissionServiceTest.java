package com.ecommerce.qa.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QaPermissionServiceTest {
    private final QaPermissionService permissions = new QaPermissionService();

    @Test
    void managerCannotReadQualityCenterWithOnlyBusinessPermissions() {
        assertDenied("qa:read", "MANAGER", "accounting:read,crm:manage", HttpStatus.FORBIDDEN);
    }

    @Test
    void exactPermissionAllowsOnlyTheMatchingOperation() {
        QaPermissionService.Actor actor = permissions.require("qa:read", "17", "qa@example.invalid",
                "ADMIN", "ADMIN", "qa:read,qa:run");
        assertThat(actor.id()).isEqualTo(17L);
        assertThat(actor.permissions()).contains("qa:read", "qa:run");
        assertDenied("qa:cancel", "ADMIN", "qa:read,qa:run", HttpStatus.FORBIDDEN);
    }

    @Test
    void wildcardPermissionAllowsTheRequestedQaOperation() {
        QaPermissionService.Actor actor = permissions.require("qa:cancel", "17", "root@example.invalid",
                "ADMIN", "ADMIN", "*");
        assertThat(actor.permissions()).contains("*");
    }

    @Test
    void superAdminRoleIsAllowedByTheVerifiedRoleClaim() {
        QaPermissionService.Actor actor = permissions.require("qa:run", "17", "root@example.invalid",
                "SUPER_ADMIN", "ADMIN", "");
        assertThat(actor.role()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void permissionSubstringsDoNotGrantAccess() {
        assertDenied("qa:run", "ADMIN", "qa:runner,qa:read", HttpStatus.FORBIDDEN);
    }

    @Test
    void missingOrInvalidIdentityIsRejectedBeforePermissionEvaluation() {
        assertDenied("qa:read", "ADMIN", "qa:read", HttpStatus.UNAUTHORIZED, "not-a-number");
        assertThatThrownBy(() -> permissions.require("qa:read", "", "", "ADMIN", "ADMIN", "qa:read"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void assertDenied(String permission, String role, String grants, HttpStatus expected) {
        assertDenied(permission, role, grants, expected, "17");
    }

    private void assertDenied(String permission, String role, String grants, HttpStatus expected, String id) {
        assertThatThrownBy(() -> permissions.require(permission, id, "tester@example.invalid", role, role, grants))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(expected);
    }
}

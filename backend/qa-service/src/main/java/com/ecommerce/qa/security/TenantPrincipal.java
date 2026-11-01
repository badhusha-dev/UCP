package com.ecommerce.qa.security;

import java.util.Set;
import java.util.UUID;

public record TenantPrincipal(
        long userId,
        String email,
        UUID tenantId,
        String tenantRole,
        Set<String> permissions) {
}

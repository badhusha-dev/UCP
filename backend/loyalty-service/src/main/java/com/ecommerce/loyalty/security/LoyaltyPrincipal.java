package com.ecommerce.loyalty.security;

import java.util.Set;
import java.util.UUID;

public record LoyaltyPrincipal(
        long userId,
        String email,
        UUID tenantId,
        String tenantRole,
        Set<String> permissions) {
}

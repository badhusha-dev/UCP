package com.ecommerce.cart.security;

import java.util.Set;
import java.util.UUID;

public record CartPrincipal(
        long userId,
        String email,
        UUID tenantId,
        String tenantRole,
        Set<String> permissions) {
}

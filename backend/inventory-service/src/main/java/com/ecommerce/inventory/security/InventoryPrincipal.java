package com.ecommerce.inventory.security;

import java.util.Set;
import java.util.UUID;

public record InventoryPrincipal(long userId, String email, String role, UUID tenantId,
                                 String applicationCode, Set<String> permissions) {
    public boolean hasPermission(String permission) {
        return permissions.contains("*") || permissions.contains(permission);
    }
}

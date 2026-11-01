package com.ecommerce.order.admin.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Identity of the verified E-commerce Admin caller. Everything here comes from the verified JWT, never from request headers. */
public record AdminPrincipal(long userId, String email, String role, UUID tenantId, Set<String> permissions, List<String> storeIds) {
    public boolean hasPermission(String permission) { return permissions.contains("*") || permissions.contains(permission); }

    /** Store-level authorization: users with order:view-all-stores see every store; others only the stores named in their token. */
    public boolean canSeeStore(String storeId) { return hasPermission("order:view-all-stores") || storeIds.contains(storeId); }
}

package com.ecommerce.gateway.security;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides which routes a tenant-scoped token (one carrying an {@code application_code} claim) may reach.
 *
 * Only services that verify the token themselves and filter every query by {@code tenant_id} are listed. The other
 * services trust the gateway's X-User-Role/X-User-Permissions headers and keep platform-wide data with no tenant column,
 * so forwarding a tenant administrator's role to them would expose every tenant's records. Those routes stay reachable
 * with platform tokens (no application_code) only, until each service gains tenant scoping.
 */
final class TenantRoutePolicy {

    private static final List<String> TENANT_AWARE_PREFIXES = List.of(
            "/api/delivery/",          // delivery-service: own JWT + grant check, tenant-scoped JDBC
            "/api/inventory/",         // inventory-service: InventoryTenantAuthenticationFilter
            "/api/procurement/ucp/",   // tenant-aware procurement API (Inventory & Procurement module)
            "/api/products/admin/",    // product-service: CatalogTenantAuthenticationFilter (catalog, pricing, storefront)
            "/api/accounting/admin/",  // accounting-service: AccountingAuthenticationFilter + tenant_id on every table (V19)
            "/api/orders/admin/"       // order-service: AdminAuthenticationFilter (control tower, returns)
    );

    // Inside tenant-aware prefixes but aggregated platform-wide (no tenant column / no tenant argument).
    private static final List<String> PLATFORM_WIDE_PREFIXES = List.of(
            "/api/orders/admin/analytics",   // routed to analytics-service
            "/api/products/admin/analytics"  // InventoryAnalyticsService aggregates every tenant's products
    );

    // A user's own account and its sub-resources (addresses, loyalty balance) are user-scoped, not tenant data.
    private static final Pattern OWN_ACCOUNT = Pattern.compile("^/api/users/(\\d+)(?:/.*)?$");

    private TenantRoutePolicy() {
    }

    static boolean allows(String path, Object userId) {
        for (String prefix : PLATFORM_WIDE_PREFIXES) {
            if (path.startsWith(prefix)) return false;
        }
        if (path.equals("/api/delivery") || path.equals("/api/products/admin") || path.equals("/api/orders/admin")) return true;
        for (String prefix : TENANT_AWARE_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        // Public catalog and per-user cart: user-scoped, and neither trusts the forwarded role header.
        if ((path.equals("/api/products") || path.startsWith("/api/products/")) && !path.startsWith("/api/products/admin")) return true;
        if ((path.equals("/api/cart") || path.startsWith("/api/cart/")) && !path.startsWith("/api/cart/admin")) return true;
        var ownAccount = OWN_ACCOUNT.matcher(path);
        if (ownAccount.matches()) {
            return userId != null && ownAccount.group(1).equals(String.valueOf(userId))
                    && !path.matches("^/api/users/\\d+/(role|status|loyalty/earn)(/.*)?$");
        }
        return false;
    }
}

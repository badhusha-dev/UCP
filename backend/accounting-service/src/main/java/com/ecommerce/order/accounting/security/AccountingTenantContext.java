package com.ecommerce.order.accounting.security;

import java.util.UUID;

/**
 * Tenant of the current request. Set only by {@link AccountingAuthenticationFilter} from a verified token; everything else
 * (Kafka order events, customer endpoints, internal callbacks, platform tokens) runs in the legacy tenant, which is where all
 * pre-tenancy accounting data lives (Flyway V19 default).
 */
public final class AccountingTenantContext {
    public static final UUID LEGACY_TENANT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private AccountingTenantContext() {
    }

    public static UUID current() {
        UUID tenant = CURRENT.get();
        return tenant == null ? LEGACY_TENANT_ID : tenant;
    }

    static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    static void clear() {
        CURRENT.remove();
    }
}

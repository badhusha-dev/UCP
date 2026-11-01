package com.ecommerce.delivery.service;

import com.ecommerce.delivery.controller.DeliveryCommands;
import com.ecommerce.delivery.security.DeliveryPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the Delivery Flyway schema and service against a real PostgreSQL database and proves that
 * every delivery read and write is scoped to the caller's tenant. Requires UCP_TEST_DELIVERY_POSTGRES_URL
 * pointing at an isolated, disposable database; the test is skipped otherwise.
 */
@SpringBootTest(properties = {
        "jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.flyway.enabled=true",
        "delivery.assignment-scan-ms=3600000",
        "delivery.retention-cron=-"
})
class DeliveryTenantIsolationPostgresIntegrationTest {
    private static final String DATABASE_URL = System.getenv("UCP_TEST_DELIVERY_POSTGRES_URL");
    private static final Set<String> DISPATCHER = Set.of("delivery:read", "delivery:manage", "delivery:dispatch");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry properties) {
        if (DATABASE_URL == null || DATABASE_URL.isBlank()) return;
        properties.add("spring.datasource.url", () -> DATABASE_URL);
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("UCP_TEST_DELIVERY_POSTGRES_USERNAME", "postgres"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("UCP_TEST_DELIVERY_POSTGRES_PASSWORD", ""));
    }

    @BeforeAll
    static void requireIsolatedPostgres() {
        assumeTrue(DATABASE_URL != null && !DATABASE_URL.isBlank(),
                "Delivery tenant isolation test needs UCP_TEST_DELIVERY_POSTGRES_URL.");
    }

    @MockBean
    private DeliveryOutboxPublisher outboxPublisher;

    @Autowired
    private DeliveryService deliveries;

    @Autowired
    private JdbcTemplate jdbc;

    // Fresh tenants per run so the shared test database never needs destructive cleanup.
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final DeliveryPrincipal dispatcherA = actor(10L, tenantA, "DISPATCHER", DISPATCHER);
    private final DeliveryPrincipal dispatcherB = actor(20L, tenantB, "DISPATCHER", DISPATCHER);

    @Test
    void anotherTenantCannotReadOrListADelivery() {
        String id = create(dispatcherA, "ORD-" + UUID.randomUUID(), UUID.randomUUID().toString());

        assertEquals(tenantA.toString(), deliveries.getDelivery(dispatcherA, UUID.fromString(id)).get("tenantId"));
        assertStatus(HttpStatus.NOT_FOUND, () -> deliveries.getDelivery(dispatcherB, UUID.fromString(id)));
        List<Map<String, Object>> visibleToB = deliveries.listDeliveries(dispatcherB, null, 100);
        assertTrue(visibleToB.stream().noneMatch(row -> id.equals(row.get("id"))));
    }

    @Test
    void anotherTenantCannotTransitionADeliveryAndItsStateIsUnchanged() {
        String id = create(dispatcherA, "ORD-" + UUID.randomUUID(), UUID.randomUUID().toString());
        var cancel = new DeliveryCommands.Transition("CANCELLED", "Cross-tenant attempt", 0L);

        assertStatus(HttpStatus.NOT_FOUND,
                () -> deliveries.transition(dispatcherB, UUID.fromString(id), cancel, UUID.randomUUID().toString()));
        assertEquals("CREATED", jdbc.queryForObject("SELECT status FROM delivery WHERE id=?::uuid", String.class, id));
    }

    @Test
    void orderReferencesAreUniquePerTenantNotGlobally() {
        String orderReference = "ORD-" + UUID.randomUUID();
        create(dispatcherA, orderReference, UUID.randomUUID().toString());

        assertDoesNotThrow(() -> create(dispatcherB, orderReference, UUID.randomUUID().toString()));
        assertStatus(HttpStatus.CONFLICT, () -> create(dispatcherA, orderReference, UUID.randomUUID().toString()));
    }

    @Test
    void replayingAnIdempotencyKeyReturnsTheOriginalDelivery() {
        String orderReference = "ORD-" + UUID.randomUUID();
        String key = UUID.randomUUID().toString();

        assertEquals(create(dispatcherA, orderReference, key), create(dispatcherA, orderReference, key));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM delivery WHERE tenant_id=? AND order_reference=?",
                Integer.class, tenantA, orderReference));
    }

    @Test
    void unfilteredIncidentListIsScopedToTheTenant() {
        DeliveryPrincipal incidentsB = actor(21L, tenantB, "DISPATCHER", Set.of("delivery:read", "delivery:incident"));

        assertTrue(deliveries.incidents(incidentsB, null).isEmpty());
    }

    @Test
    void creatingADeliveryRequiresTheManagePermission() {
        DeliveryPrincipal analyst = actor(30L, tenantA, "DELIVERY_ANALYST", Set.of("delivery:read", "delivery:report"));

        assertStatus(HttpStatus.FORBIDDEN, () -> create(analyst, "ORD-" + UUID.randomUUID(), UUID.randomUUID().toString()));
    }

    private String create(DeliveryPrincipal actor, String orderReference, String idempotencyKey) {
        var request = new DeliveryCommands.CreateDelivery(orderReference, "RETAIL", null,
                "Warehouse 1", "1 Depot Road", "Customer", "2 Main Street", "560001", null,
                new BigDecimal("1.5"), 1, null);
        return String.valueOf(deliveries.createDelivery(actor, request, idempotencyKey).get("id"));
    }

    private static DeliveryPrincipal actor(long userId, UUID tenantId, String role, Set<String> permissions) {
        return new DeliveryPrincipal(userId, "user" + userId + "@example.test", role, tenantId, "DELIVERY_OPS", permissions);
    }

    private static void assertStatus(HttpStatus expected, Runnable call) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(expected, error.getStatusCode());
    }
}

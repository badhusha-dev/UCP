package com.ecommerce.qa.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Version-controlled feature/test inventory. Every test entry names a real test class in this repository. */
public final class QaCatalogRegistry {
    private QaCatalogRegistry() {}

    public record TestMapping(String testId, String name, String testClass, String layer, int caseCount, String permission) {}
    public record Feature(String id, String name, String domain, String frontendModule, String backendService,
                          List<String> endpoints, List<String> databases, List<String> permissions,
                          List<TestMapping> tests, String coverageNote) {
        public String coverageStatus() { return tests.isEmpty() ? "NOT_COVERED" : "PARTIAL"; }
    }
    public record Suite(String id, String name, String description, boolean executable, String runnerKey) {}
    public record Role(String key, String name, Set<String> permissions) {
        public boolean allows(String permission) { return permissions.contains("*") || permissions.contains(permission); }
    }

    private static TestMapping test(String id, String label, String className, String layer, int cases, String permission) {
        return new TestMapping(id, label, className, layer, cases, permission);
    }

    public static final List<Feature> FEATURES = List.of(
        new Feature("users", "Authentication & Users", "Identity", "mfe-user", "user-service",
            List.of("/api/users/login", "/api/users/register", "/api/users/**", "/api/access/**"), List.of("user_db"),
            List.of("users:read", "users:manage", "roles:manage"), List.of(
                test("user-auth-api", "Authentication and user API integration", "AuthAndUserIntegrationTest", "API", 5, "users:read"),
                test("user-repository", "User repository behavior", "UserRepositoryTest", "INTEGRATION", 2, ""),
                test("user-auth-ui", "Frontend authentication state", "authSlice.test.ts", "FRONTEND_UNIT", 4, "")),
            "Partial: login, user API, repository, and auth state tests exist; session expiry, token revocation, and object ownership coverage remain incomplete."),
        new Feature("catalog", "Products & Catalog", "Commerce", "mfe-product", "product-service",
            List.of("/api/products/**"), List.of("product_db", "inventory_db"), List.of("catalog:manage"), List.of(
                test("product-ai-inventory", "Advanced inventory and product suggestions", "AdvancedInventoryAndAiIntegrationTest", "INTEGRATION", 6, "catalog:manage"),
                test("product-customer", "Customer product experience", "CustomerExperienceIntegrationTest", "API", 7, ""),
                test("product-controller", "Product controller", "ProductControllerIntegrationTest", "API", 3, "catalog:manage"),
                test("product-repository", "Product persistence", "ProductRepositoryTest", "INTEGRATION", 2, ""),
                test("product-resilience", "Product dependency resilience", "ProductResilienceWireMockTest", "INTEGRATION", 2, "")),
            "Partial: product, customer experience, and resilience tests exist; route-level role matrix and complete CRUD/variant coverage are not measured."),
        new Feature("cart", "Cart & Basket", "Commerce", "mfe-cart", "cart-service",
            List.of("/api/cart/**"), List.of("cart_db", "product_db"), List.of("orders:manage"), List.of(
                test("cart-api", "Cart service integration", "CartServiceIntegrationTest", "INTEGRATION", 7, "orders:manage"),
                test("cart-ui-state", "Frontend cart reducer", "cartSlice.test.ts", "FRONTEND_UNIT", 10, "")),
            "Partial: cart persistence/API and shared cart reducer tests exist; browser checkout and concurrent stock workflows are not covered."),
        new Feature("orders", "Checkout, Orders & Fulfillment", "Commerce", "mfe-order", "order-service",
            List.of("/api/orders/**", "/api/orders/admin/analytics/**"), List.of("order_db", "cart_db", "inventory_db"), List.of("orders:manage"), List.of(
                test("order-fulfillment", "Advanced sales and fulfillment", "AdvancedSalesFulfillmentIntegrationTest", "INTEGRATION", 7, "orders:manage"),
                test("order-checkout", "Checkout integration", "OrderCheckoutIntegrationTest", "INTEGRATION", 3, "orders:manage"),
                test("order-repository", "Order repository", "OrderRepositoryTest", "INTEGRATION", 1, ""),
                test("order-resilience", "Order dependency resilience", "OrderResilienceWireMockTest", "INTEGRATION", 2, "")),
            "Partial: checkout, status, fulfillment, and resilience suites exist; customer ownership, partner assignment, and end-to-end delivery are not comprehensively tested."),
        new Feature("inventory", "Inventory & Reservations", "Operations", "mfe-inventory", "inventory-service",
            List.of("/api/inventory/**"), List.of("inventory_db", "order_db"), List.of("inventory:read", "inventory:manage"), List.of(
                test("inventory-api", "Inventory service integration", "InventoryServiceIntegrationTest", "INTEGRATION", 7, "inventory:manage")),
            "Partial: inventory integration tests exist; concurrency/overselling and full authorization matrices remain uncovered."),
        new Feature("stock", "Stock Management", "Operations", "mfe-stock", "stock-service",
            List.of("/api/stock/**"), List.of("stock_db", "inventory_db"), List.of("stock:read", "stock:manage"), List.of(
                test("stock-api", "Stock service integration", "StockServiceIntegrationTest", "INTEGRATION", 7, "stock:manage")),
            "Partial: stock service integration tests exist; cross-warehouse and authorization boundary coverage is incomplete."),
        new Feature("loyalty", "Loyalty & Rewards", "Commerce", "mfe-loyalty", "loyalty-service",
            List.of("/api/loyalty/**", "/api/users/{id}/loyalty/**"), List.of("loyalty_db", "user_db"), List.of("loyalty:manage"), List.of(
                test("loyalty-api", "Loyalty service integration", "LoyaltyServiceIntegrationTest", "INTEGRATION", 8, "loyalty:manage"),
                test("loyalty-user-api", "User loyalty and address workflows", "LoyaltyAndAddressIntegrationTest", "API", 5, "loyalty:manage")),
            "Partial: loyalty and user address integration tests exist; expiry, reversal, and full admin RBAC cases need dedicated suites."),
        new Feature("accounting", "Accounting & Governance", "Finance", "mfe-accounting", "accounting-service",
            List.of("/api/accounting/**"), List.of("accounting_db", "order_db"), List.of("accounting:read", "accounting:write", "accounting:approve", "accounting:reconcile", "accounting:import", "accounting:export"), List.of(
                test("accounting-controls", "Accounting security and controller rules", "AccountingSecurityAndControllerTest", "API", 6, "accounting:approve"),
                test("accounting-ledger", "Accounting service and ledger behavior", "AccountingServiceTest", "INTEGRATION", 12, "accounting:write")),
            "Partial: journal, controller, and service tests exist; period-end, tax, close/reopen, and full maker-checker permutations remain incomplete."),
        new Feature("crm", "Advanced CRM & Support", "Customer Operations", "mfe-crm", "crm-service",
            List.of("/api/crm/**"), List.of("crm_db", "user_db", "order_db"), List.of("crm:read", "crm:manage"), List.of(
                test("crm-staff-access", "CRM staff access filter", "StaffAccessFilterTest", "SECURITY", 2, "crm:manage")),
            "Partial: CRM staff access filter tests exist; customer/ticket ownership and service workflows are not comprehensively tested."),
        new Feature("procurement", "Procurement & Suppliers", "Operations", "mfe-procurement", "procurement-service",
            List.of("/api/procurement/**"), List.of("procurement_db", "inventory_db"), List.of("procurement:read", "procurement:manage"), List.of(),
            "Not covered: service and frontend exist, but no automated test class is present in the repository."),
        new Feature("analytics", "Analytics & Reporting", "Insights", "mfe-analytics", "analytics-service",
            List.of("/api/analytics/**", "/api/orders/admin/analytics/**"), List.of("analytics_db", "order_db"), List.of("analytics:read"), List.of(
                test("analytics-api", "Admin analytics integration", "AdminAnalyticsIntegrationTest", "API", 8, "analytics:read")),
            "Partial: analytics integration tests exist; export and cross-period permission scenarios are not fully covered."),
        new Feature("notifications", "Notifications & Delivery", "Platform", "shell-app (NotificationBell)", "notification-service",
            List.of("/api/notifications/**"), List.of("notification_db", "Kafka"), List.of(), List.of(
                test("notification-dlq", "Dead-letter queue integration", "DeadLetterQueueIntegrationTest", "INTEGRATION", 2, ""),
                test("notification-idempotency", "Consumer idempotency", "NotificationConsumerIdempotencyTest", "INTEGRATION", 2, ""),
                test("notification-repository", "Notification persistence", "NotificationRepositoryTest", "INTEGRATION", 2, "")),
            "Partial: DLQ, idempotency, and repository tests exist; external delivery-provider behavior uses no live provider and remains partially covered."),
        new Feature("error-stack", "Error Diagnostics", "Platform", "mfe-error-stack", "error-stack-service",
            List.of("/api/errors/**"), List.of("error_stack_db", "Kafka"), List.of("errors:manage"), List.of(
                test("error-stack-api", "Error stack integration", "ErrorStackIntegrationTest", "INTEGRATION", 6, "errors:manage")),
            "Partial: error ingestion and triage integration tests exist; redaction and artifact-access security need broader adversarial tests."),
        new Feature("rbac", "RBAC & Gateway Security", "Security", "shell-app", "api-gateway",
            List.of("/api/admin/**", "/api/access/**"), List.of("user_db"), List.of("roles:manage", "qa:read", "qa:run", "qa:cancel"), List.of(
                test("gateway-auth-basics", "Gateway authentication and fallback rules", "GatewaySecurityAndRoutingTest", "SECURITY", 7, ""),
                test("gateway-qa-rbac", "Quality Center role and permission enforcement", "GatewayQualityCenterAuthorizationTest", "RBAC", 5, "qa:read"),
                test("qa-permission-policy", "Quality Center server-side permission policy", "QaPermissionServiceTest", "RBAC", 6, "qa:run")),
            "Partial: existing gateway auth checks plus positive/negative QA permission tests; platform-wide role-operation and object-ownership matrices are not yet automated."),
        new Feature("platform", "Service Discovery & Configuration", "Platform", "shell-app", "discovery-server, config-server",
            List.of("/actuator/health", "/v3/api-docs/**"), List.of("service registry", "config-repo"), List.of(), List.of(
                test("config-server-startup", "Config server application startup", "ConfigServerApplicationTest", "SMOKE", 1, ""),
                test("discovery-startup", "Discovery server application startup", "DiscoveryServerApplicationTest", "SMOKE", 1, "")),
            "Partial: application startup checks exist; service registration and configuration-refresh workflows are not comprehensively tested."),
        new Feature("quality-center", "Quality Center", "Platform Quality", "shell-app", "qa-service",
            List.of("/api/admin/qa/**"), List.of("qa_db", "isolated runner workspace"), List.of("qa:read", "qa:run", "qa:cancel"), List.of(
                test("qa-catalog-registry", "Catalog registry integrity", "QaCatalogRegistryTest", "UNIT", 5, ""),
                test("qa-run-selection", "Allow-listed run selection", "QaRunSelectionTest", "UNIT", 5, "qa:run")),
            "Partial: catalog and suite-selection policy are tested; full browser workflows and long-running runner integration are recorded as follow-up coverage." )
    );

    public static final List<Suite> SUITES = List.of(
        new Suite("all", "Run All Tests", "Run the complete trusted backend suite and shared frontend tests in the local isolated runner.", true, "ALL"),
        new Suite("backend", "Backend Tests", "Run the repository Maven test suite in the isolated runner workspace.", true, "BACKEND"),
        new Suite("frontend", "Frontend Tests", "Run the existing shared-library Node test suite; browser E2E suites are not registered yet.", true, "FRONTEND"),
        new Suite("rbac", "RBAC Tests", "Run real gateway positive/negative QA permission tests and server-side QA permission policy tests.", true, "RBAC"),
        new Suite("integration", "Integration Tests", "Run version-controlled backend test classes named *IntegrationTest.", true, "INTEGRATION"),
        new Suite("regression", "Regression Tests", "Run the same complete trusted suite as Run All Tests and compare with the previous full run.", true, "ALL"),
        new Suite("smoke", "Smoke Tests", "A dedicated business smoke plan has not been registered yet.", false, "UNAVAILABLE"),
        new Suite("e2e", "End-to-End Tests", "No trusted browser E2E suite is currently present in this repository.", false, "UNAVAILABLE")
    );

    public static final List<Role> DEFAULT_ROLES = List.of(
        new Role("SUPER_ADMIN", "Super Admin", Set.of("*")),
        new Role("ADMIN", "Administrator", Set.of("qa:read", "qa:run", "qa:cancel", "users:read", "users:manage", "roles:manage", "accounting:read", "accounting:write", "accounting:approve", "accounting:reconcile", "accounting:import", "accounting:export", "crm:read", "crm:manage", "inventory:read", "inventory:manage", "stock:read", "stock:manage", "analytics:read", "loyalty:manage", "orders:manage", "procurement:read", "procurement:manage", "errors:manage", "catalog:manage")),
        new Role("MANAGER", "Manager", Set.of("users:read", "users:manage", "roles:manage", "accounting:read", "accounting:write", "accounting:approve", "accounting:reconcile", "accounting:import", "accounting:export", "crm:read", "crm:manage", "inventory:read", "inventory:manage", "stock:read", "stock:manage", "analytics:read", "loyalty:manage", "orders:manage", "procurement:read", "procurement:manage", "errors:manage")),
        new Role("ACCOUNTANT", "Accountant", Set.of("accounting:read", "accounting:write", "accounting:reconcile", "accounting:import", "accounting:export")),
        new Role("AUDITOR", "Auditor", Set.of("accounting:read", "accounting:export", "analytics:read")),
        new Role("USER", "Standard User", Set.of())
    );

    public static Map<String, Feature> featureById() {
        return FEATURES.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Feature::id, f -> f));
    }

    public static Feature featureForClass(String className) {
        String shortName = className == null ? "" : className.substring(className.lastIndexOf('.') + 1);
        return FEATURES.stream().filter(f -> f.tests().stream().anyMatch(t -> t.testClass().equals(shortName))).findFirst().orElse(null);
    }

    public static String serviceForModule(String module) {
        return FEATURES.stream().filter(f -> f.backendService().equals(module)).map(Feature::id).findFirst().orElse("platform");
    }
}

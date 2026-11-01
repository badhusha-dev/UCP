package com.ecommerce.user;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the full RBAC model:
 *  - Every role can complete platform login and the JWT contains the expected role and permissions.
 *  - Application-scoped login succeeds for representative roles across every active application.
 *  - Tenant and application boundary checks return 403 when grants or entitlements are absent.
 *
 * Requires Docker (Testcontainers) or UCP_TEST_POSTGRES_URL set to an external database.
 * Flyway runs V1–V18, so QA_ANALYST (V18) is present when the tests execute.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacRolePermissionTest {

    private static final String TEST_PASSWORD = "Rbac-Test-Pass-123!";
    private static final String EXTERNAL_DATABASE_URL = System.getenv("UCP_TEST_POSTGRES_URL");

    static PostgreSQLContainer<?> POSTGRES;

    @BeforeAll
    static void startPostgres() {
        if (EXTERNAL_DATABASE_URL != null && !EXTERNAL_DATABASE_URL.isBlank()) return;
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("user_rbac_test")
                .withUsername("test")
                .withPassword("test");
        try {
            POSTGRES.start();
        } catch (RuntimeException unavailable) {
            Assumptions.assumeTrue(false, "Needs Docker or UCP_TEST_POSTGRES_URL.");
        }
    }

    @AfterAll
    static void stopContainer() {
        if (POSTGRES != null && POSTGRES.isRunning()) POSTGRES.stop();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry properties) {
        boolean external = EXTERNAL_DATABASE_URL != null && !EXTERNAL_DATABASE_URL.isBlank();
        properties.add("spring.datasource.url", external ? () -> EXTERNAL_DATABASE_URL : POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", external
                ? () -> System.getenv().getOrDefault("UCP_TEST_POSTGRES_USERNAME", "postgres") : POSTGRES::getUsername);
        properties.add("spring.datasource.password", external
                ? () -> System.getenv().getOrDefault("UCP_TEST_POSTGRES_PASSWORD", "") : POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.cloud.config.enabled", () -> "false");
        properties.add("eureka.client.enabled", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtUtil jwt;

    // ---- Platform login: every RBAC role can log in and JWT is correct ----

    @ParameterizedTest(name = "{0}: platform login and JWT claims")
    @MethodSource("rolesAndKeyPermissions")
    void platformLogin_succeeds_andJwtHasCorrectClaims(String roleKey, String mustHavePermission) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = newUser("plt." + roleKey.toLowerCase() + "." + suffix + "@rbac.test", roleKey);

        String body = mvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getEmail(), TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Claims claims = jwt.getClaims(mapper.readTree(body).get("token").asText());
        assertThat(claims.get("role", String.class)).isEqualTo(roleKey);

        @SuppressWarnings("unchecked")
        List<String> perms = (List<String>) claims.get("permissions");
        if ("SUPER_ADMIN".equals(roleKey)) {
            assertThat(perms).containsExactly("*");
        } else if (mustHavePermission != null) {
            assertThat(perms).as("role %s must contain permission %s", roleKey, mustHavePermission)
                    .contains(mustHavePermission);
        } else {
            assertThat(perms).isNotNull(); // USER: authenticated but no explicit RBAC grant
        }
    }

    static Stream<Arguments> rolesAndKeyPermissions() {
        return Stream.of(
            // Platform
            arguments("SUPER_ADMIN",          null),
            arguments("ADMIN",                "users:manage"),
            arguments("MANAGER",              "roles:manage"),
            arguments("ACCOUNTANT",           "accounting:reconcile"),
            arguments("AUDITOR",              "analytics:read"),
            arguments("USER",                 null),
            // Delivery
            arguments("DELIVERY_ADMIN",       "delivery:manage"),
            arguments("DISPATCHER",           "delivery:dispatch"),
            arguments("DELIVERY_PARTNER",     "delivery:partner:self"),
            arguments("DELIVERY_ANALYST",     "delivery:report"),
            arguments("DELIVERY_RECIPIENT",   "delivery:track:own"),
            // Warehouse / Inventory
            arguments("WAREHOUSE_OPERATOR",   "goods-receipt:manage"),
            arguments("INVENTORY_MANAGER",    "inventory:adjust-approve"),
            arguments("INVENTORY_ANALYST",    "inventory:export"),
            // Procurement
            arguments("PROCUREMENT_MANAGER",  "purchase-order:approve"),
            arguments("PROCUREMENT_OFFICER",  "purchase-order:manage"),
            // Orders
            arguments("ORDER_MANAGER",        "order:settings"),
            arguments("ORDER_OPERATOR",       "order:process"),
            arguments("ORDER_SUPPORT",        "order:customer:view"),
            arguments("ORDER_ANALYST",        "order:report:view"),
            // Returns
            arguments("RETURNS_MANAGER",      "return:policy-approve"),
            arguments("RETURNS_AGENT",        "return:create"),
            arguments("RETURNS_ANALYST",      "return:analytics-view"),
            arguments("WAREHOUSE_INSPECTOR",  "return:inspect"),
            arguments("REFUND_APPROVER",      "return:refund-approve"),
            // Pricing / Promotions
            arguments("PRICING_MANAGER",      "pricing:cost:read"),
            arguments("PRICING_APPROVER",     "pricing:approve"),
            arguments("PRICING_ANALYST",      "pricing:audit"),
            arguments("MARKETING_MANAGER",    "coupon:manage"),
            // Storefront
            arguments("STOREFRONT_MANAGER",   "storefront:domain"),
            arguments("STOREFRONT_PUBLISHER", "storefront:publish"),
            arguments("CONTENT_EDITOR",       "storefront:edit"),
            // Quality Center (V18)
            arguments("QA_ANALYST",           "qa:read")
        );
    }

    // ---- Application-scoped login: representative role per active application ----

    @ParameterizedTest(name = "{0} → {1} application login")
    @MethodSource("applicationLoginCases")
    void applicationLogin_succeeds_andJwtHasExpectedClaims(
            String roleKey, String appCode, String mustHavePermission) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User root   = newUser("root-" + suffix + "@rbac.test", "SUPER_ADMIN");
        User worker = newUser("worker-" + suffix + "@rbac.test", roleKey);
        String rootToken = jwt.generateToken(root.getId(), root.getEmail(), root.getName(),
                root.getRole(), Set.of("*"));

        String tenantId = createTenant(rootToken, "AppTest " + suffix, "app-" + suffix);
        enableApp(rootToken, tenantId, appCode);
        addMember(rootToken, tenantId, worker.getEmail());
        grantApp(rootToken, tenantId, worker.getEmail(), appCode, roleKey);

        String response = mvc.perform(post("/api/users/login/application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appLoginBody(worker.getEmail(), TEST_PASSWORD, appCode, "app-" + suffix)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = mapper.readTree(response);
        assertThat(json.get("applicationCode").asText()).isEqualTo(appCode);

        Claims claims = jwt.getClaims(json.get("token").asText());
        assertThat(claims.get("application_code", String.class)).isEqualTo(appCode);
        assertThat(claims.get("role", String.class)).isEqualTo(roleKey);

        @SuppressWarnings("unchecked")
        List<String> perms = (List<String>) claims.get("permissions");
        assertThat(perms).as("application token for %s must contain %s", roleKey, mustHavePermission)
                .contains(mustHavePermission);
    }

    static Stream<Arguments> applicationLoginCases() {
        return Stream.of(
            arguments("DELIVERY_PARTNER",   "DELIVERY_OPS",    "delivery:partner:self"),
            arguments("WAREHOUSE_OPERATOR", "INVENTORY",       "inventory:view"),
            arguments("ACCOUNTANT",         "ACCOUNTING",      "accounting:reconcile"),
            arguments("ORDER_MANAGER",      "ECOMMERCE_ADMIN", "order:settings"),
            arguments("QA_ANALYST",         "QUALITY_CENTER",  "qa:read")
        );
    }

    // ---- Boundary enforcement ----

    @Test
    void platformTenantEndpoint_returnsForbidden_forNonSuperAdmin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = newUser("admin-b-" + suffix + "@rbac.test", "ADMIN");
        String adminToken = jwt.generateToken(admin.getId(), admin.getEmail(), admin.getName(), admin.getRole());
        mvc.perform(get("/api/platform/tenants").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicationLogin_returnsForbidden_whenGrantIsAbsent() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User root      = newUser("root-ng-" + suffix + "@rbac.test", "SUPER_ADMIN");
        User accountant = newUser("acc-ng-" + suffix + "@rbac.test", "ACCOUNTANT");
        String rootToken = jwt.generateToken(root.getId(), root.getEmail(), root.getName(),
                root.getRole(), Set.of("*"));

        String tenantId = createTenant(rootToken, "No Grant " + suffix, "nogrant-" + suffix);
        enableApp(rootToken, tenantId, "ACCOUNTING");
        addMember(rootToken, tenantId, accountant.getEmail());
        // No grantApp — user has a membership but no application grant.

        mvc.perform(post("/api/users/login/application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appLoginBody(accountant.getEmail(), TEST_PASSWORD, "ACCOUNTING", "nogrant-" + suffix)))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicationLogin_returnsForbidden_forWrongTenant() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User root    = newUser("root-wt-" + suffix + "@rbac.test", "SUPER_ADMIN");
        User partner = newUser("partner-wt-" + suffix + "@rbac.test", "DELIVERY_PARTNER");
        String rootToken = jwt.generateToken(root.getId(), root.getEmail(), root.getName(),
                root.getRole(), Set.of("*"));

        // Grant DELIVERY_OPS on tenant A only.
        String tenantA = createTenant(rootToken, "Tenant A " + suffix, "tenant-a-" + suffix);
        enableApp(rootToken, tenantA, "DELIVERY_OPS");
        addMember(rootToken, tenantA, partner.getEmail());
        grantApp(rootToken, tenantA, partner.getEmail(), "DELIVERY_OPS", "DELIVERY_PARTNER");

        // Tenant B exists; partner has no grant there.
        createTenant(rootToken, "Tenant B " + suffix, "tenant-b-" + suffix);

        mvc.perform(post("/api/users/login/application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appLoginBody(partner.getEmail(), TEST_PASSWORD, "DELIVERY_OPS", "tenant-b-" + suffix)))
                .andExpect(status().isForbidden());
    }

    // ---- Helpers ----

    private User newUser(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setName(role + " Test User");
        user.setPassword(passwords.encode(TEST_PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return users.saveAndFlush(user);
    }

    private String createTenant(String rootToken, String name, String slug) throws Exception {
        String result = mvc.perform(post("/api/platform/tenants")
                        .header("Authorization", bearer(rootToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("displayName", name, "slug", slug))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).get("id").asText();
    }

    private void enableApp(String rootToken, String tenantId, String appCode) throws Exception {
        mvc.perform(put("/api/platform/tenants/{t}/applications/{a}", tenantId, appCode)
                        .header("Authorization", bearer(rootToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNoContent());
    }

    private void addMember(String rootToken, String tenantId, String email) throws Exception {
        mvc.perform(post("/api/platform/tenants/{t}/memberships", tenantId)
                        .header("Authorization", bearer(rootToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());
    }

    private void grantApp(String rootToken, String tenantId, String email, String appCode, String roleKey) throws Exception {
        mvc.perform(put("/api/platform/tenants/{t}/memberships/{e}/applications/{a}", tenantId, email, appCode)
                        .header("Authorization", bearer(rootToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleKey\":\"" + roleKey + "\"}"))
                .andExpect(status().isNoContent());
    }

    private String loginBody(String email, String password) throws Exception {
        return mapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String appLoginBody(String email, String password, String appCode, String tenantSlug) throws Exception {
        return mapper.writeValueAsString(Map.of("email", email, "password", password,
                "applicationCode", appCode, "tenantSlug", tenantSlug));
    }

    private static String bearer(String token) { return "Bearer " + token; }
}

package com.ecommerce.user;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
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

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantAccessPostgresIntegrationTest {
    private static final String SERVICE_TOKEN = "integration-test-service-token";
    private static final String EXTERNAL_DATABASE_URL = System.getenv("UCP_TEST_POSTGRES_URL");

    static PostgreSQLContainer<?> POSTGRES;

    @BeforeAll
    static void startPostgresWhenNoExternalTestDatabaseWasProvided() {
        if (EXTERNAL_DATABASE_URL != null && !EXTERNAL_DATABASE_URL.isBlank()) return;
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("user_service_test")
                .withUsername("test")
                .withPassword("test");
        try {
            POSTGRES.start();
        } catch (RuntimeException unavailable) {
            Assumptions.assumeTrue(false, "PostgreSQL integration test needs Docker or UCP_TEST_POSTGRES_URL.");
        }
    }

    @AfterAll
    static void stopContainerIfStarted() {
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
        properties.add("SERVICE_TO_SERVICE_TOKEN", () -> SERVICE_TOKEN);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtUtil jwt;

    @Test
    void applicationGrantIsTenantScopedRevocableAndAuditedInPostgres() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String clientASlug = "client-a-" + suffix;
        User root = createUser("root-" + suffix + "@ucp.test", "Platform Root", "SUPER_ADMIN");
        User employee = createUser("employee-" + suffix + "@ucp.test", "Warehouse Employee", "USER");
        User unassigned = createUser("unassigned-" + suffix + "@ucp.test", "Unassigned User", "USER");
        String rootToken = jwt.generateToken(root.getId(), root.getEmail(), root.getName(), root.getRole(), Set.of("*"));
        String employeeLegacyToken = jwt.generateToken(employee.getId(), employee.getEmail(), employee.getName(), employee.getRole());

        mvc.perform(get("/api/platform/tenants").header("Authorization", bearer(employeeLegacyToken)))
                .andExpect(status().isForbidden());

        String tenantA = createTenant(rootToken, "Client A", clientASlug);
        String tenantB = createTenant(rootToken, "Client B", "client-b-" + suffix);
        mvc.perform(put("/api/platform/tenants/{tenantId}/applications/INVENTORY", tenantA)
                        .header("Authorization", bearer(rootToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNoContent());

        addMember(rootToken, tenantA, employee.getEmail());
        addMember(rootToken, tenantB, employee.getEmail());
        mvc.perform(put("/api/platform/tenants/{tenantId}/memberships/{email}/applications/INVENTORY", tenantA, employee.getEmail())
                        .header("Authorization", bearer(rootToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleKey\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/platform/tenants/{tenantId}/memberships/{email}/applications/INVENTORY", tenantA, employee.getEmail())
                        .header("Authorization", bearer(rootToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleKey\":\"WAREHOUSE_OPERATOR\"}"))
                .andExpect(status().isNoContent());

        String scopedToken = mvc.perform(post("/api/users/login/application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(employee.getEmail(), "Employee-Password-123!", "INVENTORY", clientASlug)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String appToken = mapper.readTree(scopedToken).get("token").asText();
        assertThat(jwt.getClaims(appToken).get("tenant_id", String.class)).isEqualTo(tenantA);
        assertThat(jwt.getClaims(appToken).get("application_code", String.class)).isEqualTo("INVENTORY");

        mvc.perform(post("/api/users/login/application").contentType(MediaType.APPLICATION_JSON)
                        .content(login(employee.getEmail(), "Employee-Password-123!", "INVENTORY", "client-b-" + suffix)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users/login/application").contentType(MediaType.APPLICATION_JSON)
                        .content(login(unassigned.getEmail(), "Employee-Password-123!", "INVENTORY", clientASlug)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/users/internal/authorization/check")
                        .header("X-Service-Token", SERVICE_TOKEN).header("Authorization", bearer(appToken)))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/platform/tenants/{tenantId}/memberships/{email}/applications/INVENTORY", tenantA, employee.getEmail())
                        .header("Authorization", bearer(rootToken)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/users/internal/authorization/check")
                        .header("X-Service-Token", SERVICE_TOKEN).header("Authorization", bearer(appToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users/login/application").contentType(MediaType.APPLICATION_JSON)
                        .content(login(employee.getEmail(), "Employee-Password-123!", "INVENTORY", clientASlug)))
                .andExpect(status().isForbidden());

        String audit = mvc.perform(get("/api/platform/tenants/{tenantId}/audit", tenantA)
                        .header("Authorization", bearer(rootToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode events = mapper.readTree(audit);
        assertThat(events.toString()).contains("TENANT_CREATE", "APPLICATION_ENABLE", "MEMBERSHIP_CREATE",
                "APPLICATION_GRANT", "APPLICATION_GRANT_REVOKE");
        assertThat(tenantB).isNotEqualTo(tenantA);
    }

    private User createUser(String email, String name, String role) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPassword(passwords.encode("Employee-Password-123!"));
        user.setRole(role);
        user.setActive(true);
        return users.saveAndFlush(user);
    }

    private String createTenant(String token, String name, String slug) throws Exception {
        String result = mvc.perform(post("/api/platform/tenants")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("displayName", name, "slug", slug))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).get("id").asText();
    }

    private void addMember(String token, String tenantId, String email) throws Exception {
        mvc.perform(post("/api/platform/tenants/{tenantId}/memberships", tenantId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("email", email))))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password, String app, String tenant) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("email", email, "password", password,
                "applicationCode", app, "tenantSlug", tenant));
    }

    private static String bearer(String token) { return "Bearer " + token; }
}

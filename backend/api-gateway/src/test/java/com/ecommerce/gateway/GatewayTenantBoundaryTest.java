package com.ecommerce.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 tenant boundary at the gateway. Allowed requests are forwarded to an unused port, so a 5xx proves the
 * gateway let them through; 401/403 proves it blocked them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayTenantBoundaryTest {
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String TENANT = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    @Autowired
    private WebTestClient client;

    @Test
    @DisplayName("A path that merely contains 'swagger' or 'api-docs' no longer skips authentication")
    void substringOfOpenEndpointDoesNotBypassAuthentication() {
        client.get().uri("/api/orders/admin/orders/swagger").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        client.get().uri("/api/users/7/api-docs").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        client.get().uri("/api/notifications/actuator").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        client.get().uri("/api/products/admin/fallback").exchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Real open endpoints stay anonymous")
    void openEndpointsStayOpen() {
        client.post().uri("/api/users/login").exchange().expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
        client.post().uri("/api/users/login/application").exchange().expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
        client.post().uri("/api/users/register").exchange().expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    @DisplayName("A tenant admin token cannot reach legacy services that trust the role header and hold every tenant's data")
    void tenantTokenCannotReachPlatformWideServices() {
        String tenantAdmin = tenantToken("ECOMMERCE_ADMIN", "ADMIN", List.of("*"));
        client.get().uri("/api/orders").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/notifications").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/admin/qa/runs").header(HttpHeaders.AUTHORIZATION, tenantToken("QUALITY_CENTER", "ADMIN", List.of("qa:read")))
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/products/admin/analytics/inventory").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/orders/admin/analytics/summary").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.put().uri("/api/users/42/role").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("A tenant token still reaches the tenant-aware admin APIs and the user's own account")
    void tenantTokenReachesTenantAwareRoutes() {
        String tenantAdmin = tenantToken("ECOMMERCE_ADMIN", "ADMIN", List.of("order:view", "pricing:read", "catalog:manage"));
        client.get().uri("/api/orders/admin/orders").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().value(s -> assertThat(s).isGreaterThanOrEqualTo(500));
        client.get().uri("/api/products/admin/pricing/dashboard").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().value(s -> assertThat(s).isGreaterThanOrEqualTo(500));
        client.get().uri("/api/users/42").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().value(s -> assertThat(s).isGreaterThanOrEqualTo(500));
        client.get().uri("/api/users/43").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.get().uri("/api/products/123").header(HttpHeaders.AUTHORIZATION, tenantAdmin)
                .exchange().expectStatus().value(s -> assertThat(s).isGreaterThanOrEqualTo(500));
    }

    @Test
    @DisplayName("An Accounting tenant token reaches the tenant-scoped accounting admin API but not the customer accounting API")
    void accountingTenantTokenReachesAdminOnly() {
        String accountant = tenantToken("ACCOUNTING", "ACCOUNTANT", List.of("accounting:read"));
        client.get().uri("/api/accounting/admin/accounts").header(HttpHeaders.AUTHORIZATION, accountant)
                .exchange().expectStatus().value(s -> assertThat(s).isGreaterThanOrEqualTo(500));
        client.get().uri("/api/accounting/balance").header(HttpHeaders.AUTHORIZATION, accountant)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        client.post().uri("/api/accounting/admin/journals").header(HttpHeaders.AUTHORIZATION, accountant)
                .exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN); // accounting:write required by the gateway route map
    }

    @Test
    @DisplayName("Platform tokens (no application_code) keep their existing access")
    void platformTokenUnchanged() {
        String platformAdmin = platformToken(List.of("users:read", "orders:manage"));
        client.get().uri("/api/users").header(HttpHeaders.AUTHORIZATION, platformAdmin)
                .exchange().expectStatus().value(s -> assertThat(s).isGreaterThanOrEqualTo(500));
        client.get().uri("/api/orders").header(HttpHeaders.AUTHORIZATION, platformAdmin)
                .exchange().expectStatus().value(s -> assertThat(s).isGreaterThanOrEqualTo(500));
    }

    private static String tenantToken(String application, String role, List<String> permissions) {
        return "Bearer " + Jwts.builder()
                .claim("id", 42L).claim("role", role).claim("tenant_role", role)
                .claim("tenant_id", TENANT).claim("application_code", application)
                .claim("permissions", permissions)
                .setSubject("tenant-admin@example.test").setId(UUID.randomUUID().toString())
                .setIssuer("universal-commerce").setAudience("commerce-api")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256).compact();
    }

    private static String platformToken(List<String> permissions) {
        return "Bearer " + Jwts.builder()
                .claim("id", 7L).claim("role", "ADMIN").claim("permissions", permissions)
                .setSubject("platform-admin@example.test")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256).compact();
    }
}

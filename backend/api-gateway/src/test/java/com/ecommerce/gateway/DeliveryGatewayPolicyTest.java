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
 * Delivery routes require a tenant-scoped DELIVERY_OPS token plus the route's permission. Allowed requests are
 * forwarded to an unused port, so a 5xx proves the gateway let them through; 403 proves it blocked them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DeliveryGatewayPolicyTest {
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String TENANT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String DELIVERY_ID = "11111111-1111-4111-8111-111111111111";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Delivery routes reject a token issued for another application")
    void rejectsNonDeliveryApplicationToken() {
        String inventoryToken = token("INVENTORY", "WAREHOUSE_OPERATOR", List.of("delivery:read", "inventory:read"));

        webTestClient.get().uri("/api/delivery/deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + inventoryToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Delivery routes reject a legacy token without tenant claims")
    void rejectsLegacyTokenWithoutTenant() {
        String legacy = Jwts.builder()
                .claim("id", 7L)
                .claim("role", "ADMIN")
                .claim("permissions", List.of("*"))
                .setSubject("admin@example.test")
                .setIssuer("universal-commerce")
                .setAudience("commerce-api")
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();

        webTestClient.get().uri("/api/delivery/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + legacy)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("A delivery partner cannot dispatch, configure zones or read the audit log")
    void partnerCannotUseDispatcherOrAdminRoutes() {
        String partner = token("DELIVERY_OPS", "DELIVERY_PARTNER",
                List.of("delivery:read", "delivery:partner:self", "delivery:tracking", "delivery:proof", "delivery:incident"));

        webTestClient.post().uri("/api/delivery/dispatch/deliveries/" + DELIVERY_ID + "/assign")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        webTestClient.post().uri("/api/delivery/zones")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        webTestClient.get().uri("/api/delivery/audit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("A delivery partner reaches own-work routes")
    void partnerReachesOwnWorkRoutes() {
        String partner = token("DELIVERY_OPS", "DELIVERY_PARTNER", List.of("delivery:read", "delivery:partner:self"));

        webTestClient.get().uri("/api/delivery/partners/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
    }

    @Test
    @DisplayName("An analyst can read reports but cannot create deliveries")
    void analystIsReadOnly() {
        String analyst = token("DELIVERY_OPS", "DELIVERY_ANALYST",
                List.of("delivery:read", "delivery:tracking", "delivery:report", "delivery:audit"));

        webTestClient.get().uri("/api/delivery/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
        webTestClient.post().uri("/api/delivery/deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + analyst)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Order-backed creation needs delivery:manage; rule administration needs delivery:config")
    void orderCreationAndRulesAreGuarded() {
        String partner = token("DELIVERY_OPS", "DELIVERY_PARTNER", List.of("delivery:read", "delivery:partner:self", "delivery:proof"));
        String admin = token("DELIVERY_OPS", "DELIVERY_ADMIN", List.of("delivery:read", "delivery:manage", "delivery:config"));

        webTestClient.post().uri("/api/delivery/deliveries/from-order")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner).exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        webTestClient.get().uri("/api/delivery/rules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner).exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        webTestClient.post().uri("/api/delivery/rules/draft/publish")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner).exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        webTestClient.post().uri("/api/delivery/deliveries/from-order")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).exchange()
                .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
        webTestClient.get().uri("/api/delivery/rules")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).exchange()
                .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
    }

    @Test
    @DisplayName("Customer tracking routes need the recipient permission; ops tokens and partners cannot use them")
    void customerTrackingRoutesAreGuarded() {
        String recipient = token("DELIVERY_OPS", "DELIVERY_RECIPIENT", List.of("delivery:track:own"));
        String ops = token("DELIVERY_OPS", "DELIVERY_ADMIN", List.of("delivery:read", "delivery:manage", "delivery:dispatch"));
        String partner = token("DELIVERY_OPS", "DELIVERY_PARTNER", List.of("delivery:read", "delivery:partner:self"));

        webTestClient.get().uri("/api/delivery/my/deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + recipient).exchange()
                .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
        webTestClient.post().uri("/api/delivery/my/deliveries/" + DELIVERY_ID + "/delivery-code")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ops).exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        webTestClient.get().uri("/api/delivery/my/deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + partner).exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        // A recipient grant carries no operations access.
        webTestClient.get().uri("/api/delivery/deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + recipient).exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
        webTestClient.get().uri("/api/delivery/assignments/mine")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ops).exchange().expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Unmapped delivery routes are denied by default")
    void unmappedRouteIsDenied() {
        String admin = token("DELIVERY_OPS", "DELIVERY_ADMIN", List.of("delivery:read", "delivery:manage", "delivery:config"));

        webTestClient.get().uri("/api/delivery/not-a-real-route")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static String token(String applicationCode, String tenantRole, List<String> permissions) {
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .claim("id", 42L)
                .claim("role", "USER")
                .claim("tenant_role", tenantRole)
                .claim("tenant_id", TENANT)
                .claim("application_code", applicationCode)
                .claim("permissions", permissions)
                .setSubject("delivery-user@example.test")
                .setIssuer("universal-commerce")
                .setAudience("commerce-api")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }
}

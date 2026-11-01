package com.ecommerce.gateway;

import com.ecommerce.gateway.security.JwtUtil;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class GatewaySecurityAndRoutingTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtUtil jwtUtil;

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private String generateTestToken(long expirationOffsetMs) {
        return Jwts.builder()
                .setSubject("user@ecommerce.com")
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationOffsetMs))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("Should validate valid JWT token and reject expired token")
    void testJwtUtilValidation() {
        String validToken = generateTestToken(3600_000);
        assertThat(jwtUtil.validateToken(validToken)).isTrue();

        String expiredToken = generateTestToken(-1000);
        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();

        assertThat(jwtUtil.validateToken("malformed.token.string")).isFalse();
    }

    @Test
    @DisplayName("Protected endpoint should return 401 Unauthorized when Authorization header is missing")
    void testMissingAuthHeaderReturns401() {
        webTestClient.get()
                .uri("/api/orders/user/1")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Protected endpoint should return 401 Unauthorized when token is invalid")
    void testInvalidTokenReturns401() {
        webTestClient.get()
                .uri("/api/orders/user/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-value")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Internal service authorization endpoint is not routed through the public gateway")
    void testInternalAuthorizationEndpointIsHidden() {
        webTestClient.post()
                .uri("/api/users/internal/authorization/check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateTestToken(3600_000))
                .header("X-Service-Token", "caller-supplied")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Super Admin portal origin can preflight identity API requests")
    void testSuperAdminPortalOriginIsAllowedByCors() {
        webTestClient.options()
                .uri("/api/users/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:3013")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3013");
    }

    @Test
    @DisplayName("Tenant-scoped SUPER_ADMIN role cannot bypass platform authorization")
    void testTenantSuperAdminCannotAccessPlatformRoutes() {
        String token = Jwts.builder()
                .claim("id", 42L)
                .claim("role", "SUPER_ADMIN")
                .claim("tenant_role", "SUPER_ADMIN")
                .claim("tenant_id", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .claim("application_code", "INVENTORY")
                .claim("permissions", java.util.List.of("inventory:read", "inventory:manage"))
                .setSubject("tenant-user@example.test")
                .setIssuer("universal-commerce")
                .setAudience("commerce-api")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();

        webTestClient.get()
                .uri("/api/platform/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Open endpoint /fallback/user should return 503 without authentication")
    void testUserServiceFallback() {
        webTestClient.get()
                .uri("/fallback/user")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("User Service is currently unavailable"));
    }

    @Test
    @DisplayName("Open endpoint /fallback/product should return 503 without authentication")
    void testProductServiceFallback() {
        webTestClient.get()
                .uri("/fallback/product")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Product Service is currently unavailable"));
    }

    @Test
    @DisplayName("Open endpoint /fallback/order should return 503 without authentication")
    void testOrderServiceFallback() {
        webTestClient.get()
                .uri("/fallback/order")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Order Service is currently unavailable"));
    }

    @Test
    @DisplayName("Open endpoint /fallback/notification should return 503 without authentication")
    void testNotificationServiceFallback() {
        webTestClient.get()
                .uri("/fallback/notification")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Notification Service is currently unavailable"));
    }
}

package com.ecommerce.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class QualityCenterRbacTest {
    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private WebTestClient client;

    @Test
    void managerWithoutQaPermissionCannotReadQualityCenterEvenWithOtherStaffPermissions() {
        client.get().uri("/api/admin/qa/overview")
                .header(HttpHeaders.AUTHORIZATION, bearer("MANAGER", List.of("accounting:read", "crm:manage")))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminReadOnlyQaPermissionCannotStartRun() {
        client.post().uri("/api/admin/qa/runs")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", List.of("qa:read")))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminQaReadPermissionPassesGatewayAuthorization() {
        client.get().uri("/api/admin/qa/overview")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", List.of("qa:read")))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
    }

    @Test
    void adminQaRunPermissionPassesGatewayAuthorization() {
        client.post().uri("/api/admin/qa/runs")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", List.of("qa:run")))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isGreaterThanOrEqualTo(500));
    }

    @Test
    void adminQaRunPermissionDoesNotAllowCancellingAnotherRun() {
        client.post().uri("/api/admin/qa/runs/0d9d6a30-cc6d-4e6f-bf21-4ab4a32f03cc/cancel")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", List.of("qa:run")))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static String bearer(String role, List<String> permissions) {
        String token = Jwts.builder()
                .claim("id", 42L)
                .claim("role", role)
                .claim("permissions", permissions)
                .setSubject("qa-test@example.invalid")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        return "Bearer " + token;
    }
}

package com.ecommerce.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login requests that pass the limiter are forwarded to an unused port, so any status other than 429 proves the
 * limiter let them through. Each test uses its own client address because counters live for the whole context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"gateway.login-rate-limit.max-attempts=3", "gateway.login-rate-limit.window=PT10M"})
@ActiveProfiles("test")
class LoginRateLimitTest {

    @Autowired
    private WebTestClient client;

    private int post(String path, String clientIp) {
        return client.post().uri(path).header("X-Real-IP", clientIp).exchange()
                .returnResult(Void.class).getStatus().value();
    }

    @Test
    @DisplayName("Sign-in requests beyond the limit from one address get 429 with Retry-After")
    void blocksAfterTheLimit() {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(post("/api/users/login", "203.0.113.10")).isNotEqualTo(429);
        }
        client.post().uri("/api/users/login").header("X-Real-IP", "203.0.113.10").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER);
    }

    @Test
    @DisplayName("Platform and application sign-in share one limit")
    void bothLoginEndpointsShareTheLimit() {
        assertThat(post("/api/users/login", "203.0.113.20")).isNotEqualTo(429);
        assertThat(post("/api/users/login", "203.0.113.20")).isNotEqualTo(429);
        assertThat(post("/api/users/login/application", "203.0.113.20")).isNotEqualTo(429);
        assertThat(post("/api/users/login/application", "203.0.113.20")).isEqualTo(429);
    }

    @Test
    @DisplayName("Other addresses and other routes are not limited by one address's sign-in attempts")
    void otherClientsAndRoutesAreUnaffected() {
        for (int attempt = 0; attempt < 4; attempt++) post("/api/users/login", "203.0.113.30");
        assertThat(post("/api/users/login", "203.0.113.30")).isEqualTo(429);

        assertThat(post("/api/users/login", "203.0.113.31")).isNotEqualTo(429);
        assertThat(post("/api/users/register", "203.0.113.30")).isNotEqualTo(429);
        assertThat(client.get().uri("/api/products").header("X-Real-IP", "203.0.113.30").exchange()
                .returnResult(Void.class).getStatus().value()).isNotEqualTo(429);
    }
}

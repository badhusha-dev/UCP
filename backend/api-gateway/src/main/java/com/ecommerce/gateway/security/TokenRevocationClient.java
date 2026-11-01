package com.ecommerce.gateway.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class TokenRevocationClient implements TokenRevocationCheck {

    private final WebClient webClient;

    public TokenRevocationClient(
            @Qualifier("revocationWebClientBuilder") WebClient.Builder builder) {
        this.webClient = builder.baseUrl("lb://user-service").build();
    }

    // Fails open (returns false = not revoked) if user-service is unreachable, so a
    // transient outage never blocks all authenticated traffic.
    public Mono<Boolean> isRevoked(String jti) {
        if (jti == null) return Mono.just(false);
        return webClient.get()
                .uri("/api/users/internal/is-revoked?jti={jti}", jti)
                .retrieve()
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofMillis(200))
                .onErrorReturn(false);
    }
}

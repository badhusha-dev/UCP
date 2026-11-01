package com.ecommerce.gateway.security;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface TokenRevocationCheck {
    Mono<Boolean> isRevoked(String jti);
}

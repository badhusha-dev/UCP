package com.ecommerce.gateway;

import com.ecommerce.gateway.security.JwtAuthenticationFilter;
import com.ecommerce.gateway.security.JwtUtil;
import com.ecommerce.gateway.security.TokenRevocationCheck;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Spoofed identity, tenant and service headers never reach a downstream service. */
class GatewayHeaderSanitizationTest {
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String TOKEN_TENANT = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String SPOOFED_TENANT = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";

    private static final TokenRevocationCheck NO_OP_REVOCATION = jti -> Mono.just(false);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil(), NO_OP_REVOCATION);

    @Test
    @DisplayName("Authenticated: client X-Tenant-ID / X-User-* / X-Service-Token are replaced by verified claims")
    void authenticatedRequestHeadersComeFromTheToken() {
        String token = Jwts.builder()
                .claim("id", 42L).claim("role", "WAREHOUSE_OPERATOR").claim("tenant_role", "WAREHOUSE_OPERATOR")
                .claim("tenant_id", TOKEN_TENANT).claim("application_code", "INVENTORY")
                .claim("permissions", List.of("inventory:read"))
                .setSubject("operator@example.test").setId(UUID.randomUUID().toString())
                .setIssuer("universal-commerce").setAudience("commerce-api")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256).compact();

        ServerHttpRequest forwarded = forward(MockServerHttpRequest.get("/api/inventory/items")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Tenant-ID", SPOOFED_TENANT)
                .header("x-tenant-id", SPOOFED_TENANT)
                .header("X-User-Id", "1")
                .header("X-User-Role", "SUPER_ADMIN")
                .header("X-User-Permissions", "*")
                .header("X-Application-Code", "SUPER_ADMIN")
                .header("X-Service-Token", "guessed"));

        HttpHeaders headers = forwarded.getHeaders();
        assertThat(headers.get("X-Tenant-ID")).containsExactly(TOKEN_TENANT);
        assertThat(headers.get("X-User-Id")).containsExactly("42");
        assertThat(headers.get("X-User-Role")).containsExactly("WAREHOUSE_OPERATOR");
        assertThat(headers.get("X-User-Permissions")).containsExactly("inventory:read");
        assertThat(headers.get("X-Application-Code")).containsExactly("INVENTORY");
        assertThat(headers.containsKey("X-Service-Token")).isFalse();
    }

    @Test
    @DisplayName("Anonymous public route: every spoofed identity header is removed")
    void anonymousRequestHeadersAreStripped() {
        ServerHttpRequest forwarded = forward(MockServerHttpRequest.get("/api/products/5")
                .header("X-Tenant-ID", SPOOFED_TENANT)
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Roles", "ADMIN")
                .header("X-User-Email", "admin@example.test")
                .header("X-User-Permissions", "*")
                .header("X-Application-Code", "SUPER_ADMIN")
                .header("X-Service-Token", "guessed"));

        for (String header : List.of("X-Tenant-ID", "X-User-Id", "X-User-Role", "X-User-Roles", "X-User-Email",
                "X-User-Permissions", "X-Application-Code", "X-Service-Token")) {
            assertThat(forwarded.getHeaders().containsKey(header)).as(header).isFalse();
        }
    }

    private ServerHttpRequest forward(MockServerHttpRequest.BaseBuilder<?> request) {
        AtomicReference<ServerHttpRequest> seen = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            seen.set(exchange.getRequest());
            return Mono.empty();
        };
        filter.filter(MockServerWebExchange.from(request), chain).block();
        assertThat(seen.get()).as("request was forwarded").isNotNull();
        return seen.get();
    }

    private static JwtUtil jwtUtil() {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", SECRET);
        ReflectionTestUtils.setField(util, "issuer", "universal-commerce");
        ReflectionTestUtils.setField(util, "audience", "commerce-api");
        return util;
    }
}

package com.ecommerce.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits sign-in requests per client address, so one client cannot spread password guesses across many accounts.
 * The per-account lockout in user-service stops guessing against a single account; this stops the spray.
 *
 * <p>Counters are in memory, so each gateway instance limits on its own. The client address is {@code X-Real-IP},
 * which the frontend nginx (and most ingress controllers) overwrite with the real peer; without it the TCP peer is
 * used. A client that reaches the gateway directly can set {@code X-Real-IP} itself.
 */
@Component
public class LoginRateLimitFilter implements GlobalFilter, Ordered {

    private static final Set<String> LOGIN_PATHS = Set.of("/api/users/login", "/api/users/login/application");
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final boolean enabled;
    private final int maxAttempts;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(@Value("${gateway.login-rate-limit.enabled:true}") boolean enabled,
                                @Value("${gateway.login-rate-limit.max-attempts:20}") int maxAttempts,
                                @Value("${gateway.login-rate-limit.window:PT1M}") Duration window) {
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.windowMillis = window.toMillis();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!enabled || !HttpMethod.POST.equals(request.getMethod()) || !LOGIN_PATHS.contains(request.getPath().value())) {
            return chain.filter(exchange);
        }

        long now = System.currentTimeMillis();
        Window window = windows.compute(clientKey(request), (key, current) ->
                current == null || now - current.start() >= windowMillis ? new Window(now, 1) : current.next());
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= windowMillis);
        }

        if (window.count() > maxAttempts) {
            long retryAfterSeconds = Math.max(1, (window.start() + windowMillis - now + 999) / 1000);
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            return response.setComplete();
        }
        return chain.filter(exchange);
    }

    static String clientKey(ServerHttpRequest request) {
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return -2; // Before JwtAuthenticationFilter (-1); login paths are open there, so order only matters for cost.
    }

    private record Window(long start, int count) {
        Window next() {
            return new Window(start, count + 1);
        }
    }
}

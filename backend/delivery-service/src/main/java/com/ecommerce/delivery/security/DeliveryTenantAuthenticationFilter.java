package com.ecommerce.delivery.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class DeliveryTenantAuthenticationFilter extends OncePerRequestFilter {
    private final DeliveryJwtVerifier jwtVerifier;
    private final DeliveryAuthorizationClient authorizationClient;

    public DeliveryTenantAuthenticationFilter(DeliveryJwtVerifier jwtVerifier, DeliveryAuthorizationClient authorizationClient) {
        this.jwtVerifier = jwtVerifier;
        this.authorizationClient = authorizationClient;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/delivery/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(401, "A tenant-scoped Delivery token is required.");
            return;
        }
        String token = authorization.substring(7);
        if (!jwtVerifier.isValidDeliveryToken(token)) {
            response.sendError(401, "A valid tenant-scoped Delivery token is required.");
            return;
        }
        try {
            authorizationClient.requireCurrentGrant(token);
            Claims claims = jwtVerifier.parse(token);
            Set<String> permissions = new HashSet<>();
            Object raw = claims.get("permissions");
            if (raw instanceof Collection<?> values) values.forEach(value -> permissions.add(String.valueOf(value)));
            DeliveryPrincipal principal = new DeliveryPrincipal(
                    ((Number) claims.get("id")).longValue(), claims.getSubject(),
                    claims.get("tenant_role", String.class), UUID.fromString(claims.get("tenant_id", String.class)),
                    claims.get("application_code", String.class), Set.copyOf(permissions));
            var authorities = permissions.stream().map(permission -> new SimpleGrantedAuthority("PERM_" + permission)).toList();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, token, authorities));
            chain.doFilter(request, response);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value() == 403 ? 403 : 401;
            response.sendError(status, status == 403 ? "Delivery access is no longer active." : "Delivery authorization could not be verified.");
        } catch (RestClientException | DeliveryAuthorizationClient.AuthorizationUnavailableException e) {
            response.sendError(503, "Delivery authorization is temporarily unavailable.");
        } catch (RuntimeException e) {
            response.sendError(401, "Invalid Delivery authorization context.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

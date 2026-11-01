package com.ecommerce.loyalty.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class LoyaltyTenantAuthFilter extends OncePerRequestFilter {

    private final LoyaltyJwtVerifier jwtVerifier;

    public LoyaltyTenantAuthFilter(LoyaltyJwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            if (!jwtVerifier.isValidTenantToken(token)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                return;
            }
            io.jsonwebtoken.Claims claims = jwtVerifier.parse(token);
            LoyaltyPrincipal principal = new LoyaltyPrincipal(
                    ((Number) claims.get("id")).longValue(),
                    claims.getSubject(),
                    UUID.fromString(claims.get("tenant_id", String.class)),
                    tenantRole(claims),
                    permissions(claims));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        }
        chain.doFilter(request, response);
    }

    private String tenantRole(io.jsonwebtoken.Claims claims) {
        String r = claims.get("tenant_role", String.class);
        return r != null ? r : claims.get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    private Set<String> permissions(io.jsonwebtoken.Claims claims) {
        Object perms = claims.get("permissions");
        if (perms instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toSet());
        }
        return Set.of();
    }
}

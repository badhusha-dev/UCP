package com.ecommerce.order.admin.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Authenticates the Order Management API itself instead of trusting gateway-injected headers: verifies the token signature, issuer,
 * audience and application code, then re-checks with the identity service that the tenant membership and E-commerce Admin grant
 * are still active. The tenant used by every query comes from here.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {
    public static final String PREFIX = "/api/orders/admin";
    private final AdminJwtVerifier jwtVerifier;
    private final AdminAuthorizationClient authorizationClient;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith(request.getContextPath() + PREFIX); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A tenant-scoped E-commerce Admin token is required."); return; }
        String token = authorization.substring(7);
        if (!jwtVerifier.isValidAdminToken(token)) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A valid tenant-scoped E-commerce Admin token is required."); return; }
        try {
            authorizationClient.requireCurrentGrant(token);
            Claims claims = jwtVerifier.parse(token);
            Number id = claims.get("id", Number.class);
            Set<String> permissions = new HashSet<>();
            if (claims.get("permissions") instanceof Collection<?> values) values.forEach(v -> permissions.add(String.valueOf(v)));
            List<String> stores = new ArrayList<>();
            if (claims.get("store_ids") instanceof Collection<?> values) values.forEach(v -> stores.add(String.valueOf(v)));
            AdminPrincipal principal = new AdminPrincipal(id.longValue(), claims.getSubject(), claims.get("tenant_role", String.class),
                    UUID.fromString(claims.get("tenant_id", String.class)), Set.copyOf(permissions), List.copyOf(stores));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, token,
                    permissions.stream().map(p -> new SimpleGrantedAuthority("PERM_" + p)).toList()));
            chain.doFilter(request, response);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value() == 403 ? 403 : 401;
            response.sendError(status, status == 403 ? "E-commerce Admin access is no longer active." : "E-commerce Admin authorization could not be verified.");
        } catch (RestClientException | AdminAuthorizationClient.AuthorizationUnavailableException e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "E-commerce Admin authorization is temporarily unavailable.");
        } finally { SecurityContextHolder.clearContext(); }
    }
}

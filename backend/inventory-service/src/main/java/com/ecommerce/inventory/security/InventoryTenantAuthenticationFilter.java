package com.ecommerce.inventory.security;

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

@Component
@RequiredArgsConstructor
public class InventoryTenantAuthenticationFilter extends OncePerRequestFilter {
    private final InventoryJwtVerifier jwtVerifier;
    private final InventoryAuthorizationClient authorizationClient;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/inventory/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A tenant-scoped Inventory token is required.");
            return;
        }
        String token = authorization.substring(7);
        if (!jwtVerifier.isValidInventoryToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A valid tenant-scoped Inventory token is required.");
            return;
        }

        try {
            authorizationClient.requireCurrentGrant(token);
            Claims claims = jwtVerifier.parse(token);
            Number id = claims.get("id", Number.class);
            String tenantValue = claims.get("tenant_id", String.class);
            String email = claims.getSubject();
            String role = claims.get("tenant_role", String.class);
            Object rawPermissions = claims.get("permissions");
            Set<String> permissions = new HashSet<>();
            if (rawPermissions instanceof Collection<?> values) values.forEach(value -> permissions.add(String.valueOf(value)));
            InventoryPrincipal principal = new InventoryPrincipal(id.longValue(), email, role, UUID.fromString(tenantValue),
                    claims.get("application_code", String.class), Set.copyOf(permissions));
            var authorities = permissions.stream().map(permission -> new SimpleGrantedAuthority("PERM_" + permission)).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, token, authorities));
            chain.doFilter(request, response);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value() == HttpServletResponse.SC_FORBIDDEN
                    ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED;
            response.sendError(status, status == HttpServletResponse.SC_FORBIDDEN
                    ? "Inventory application access is no longer active." : "Inventory authorization could not be verified.");
        } catch (RestClientException | InventoryAuthorizationClient.AuthorizationUnavailableException e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Inventory authorization is temporarily unavailable.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

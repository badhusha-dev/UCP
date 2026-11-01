package com.ecommerce.product.security;

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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Authenticates the tenant-scoped E-commerce Admin API; the public storefront catalog stays anonymous. */
@Component
@RequiredArgsConstructor
public class CatalogTenantAuthenticationFilter extends OncePerRequestFilter {
    private final CatalogJwtVerifier jwtVerifier;
    private final CatalogAuthorizationClient authorizationClient;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/products/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A tenant-scoped E-commerce Admin token is required.");
            return;
        }
        String token = authorization.substring(7);
        if (!jwtVerifier.isValidCatalogToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A valid tenant-scoped E-commerce Admin token is required.");
            return;
        }

        try {
            authorizationClient.requireCurrentGrant(token);
            Claims claims = jwtVerifier.parse(token);
            Number id = claims.get("id", Number.class);
            Object rawPermissions = claims.get("permissions");
            Set<String> permissions = new HashSet<>();
            if (rawPermissions instanceof Collection<?> values) values.forEach(value -> permissions.add(String.valueOf(value)));
            CatalogPrincipal principal = new CatalogPrincipal(id.longValue(), claims.getSubject(),
                    claims.get("tenant_role", String.class), UUID.fromString(claims.get("tenant_id", String.class)),
                    claims.get("application_code", String.class), Set.copyOf(permissions));
            var authorities = permissions.stream().map(permission -> new SimpleGrantedAuthority("PERM_" + permission)).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, token, authorities));
            chain.doFilter(request, response);
        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value() == HttpServletResponse.SC_FORBIDDEN
                    ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED;
            response.sendError(status, status == HttpServletResponse.SC_FORBIDDEN
                    ? "E-commerce Admin access is no longer active." : "E-commerce Admin authorization could not be verified.");
        } catch (RestClientException | CatalogAuthorizationClient.AuthorizationUnavailableException e) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "E-commerce Admin authorization is temporarily unavailable.");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

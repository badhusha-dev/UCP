package com.ecommerce.procurement.ucp;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/** Verified tenant identity. Tenant and permissions come only from the signed token; never from headers, query or body. */
record UcpPrincipal(long userId, String email, UUID tenantId, Set<String> permissions) {
    boolean has(String permission) { return permissions.contains("*") || permissions.contains(permission); }
}

@Component
class UcpJwtVerifier {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.issuer:universal-commerce}") private String issuer;
    @Value("${jwt.audience:commerce-api}") private String audience;

    Claims parseValid(String token) {
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes())).build().parseClaimsJws(token).getBody();
            boolean ok = c.getExpiration() != null && c.getExpiration().after(new Date()) && issuer.equals(c.getIssuer()) && audience.equals(c.getAudience())
                    && "INVENTORY".equals(c.get("application_code", String.class)) && c.get("tenant_id", String.class) != null
                    && c.get("tenant_role", String.class) != null && c.getId() != null;
            return ok ? c : null;
        } catch (Exception e) { return null; }
    }
}

@Component
class UcpAuthorizationClient {
    private final RestClient rest = RestClient.builder().build();
    @Value("${identity.service.url:${IDENTITY_SERVICE_URL:http://localhost:8081}}") private String identityUrl;
    @Value("${SERVICE_TO_SERVICE_TOKEN:}") private String serviceToken;

    /** Re-checks the tenant grant with user-service on every request; fails closed when unavailable. */
    void requireCurrentGrant(String accessToken) {
        if (serviceToken == null || serviceToken.isBlank()) throw new IllegalStateException("authorization unavailable");
        rest.post().uri(identityUrl + "/api/users/internal/authorization/check").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Service-Token", serviceToken).retrieve().toBodilessEntity();
    }
}

@Component
class UcpAuthenticationFilter extends OncePerRequestFilter {
    static final String ATTR = "ucp.principal";
    private final UcpJwtVerifier verifier;
    private final UcpAuthorizationClient authorization;

    UcpAuthenticationFilter(UcpJwtVerifier verifier, UcpAuthorizationClient authorization) { this.verifier = verifier; this.authorization = authorization; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/procurement/ucp/"); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) { response.sendError(401, "A tenant-scoped Inventory token is required."); return; }
        String token = header.substring(7);
        Claims c = verifier.parseValid(token);
        if (c == null) { response.sendError(401, "A valid tenant-scoped Inventory token is required."); return; }
        try {
            authorization.requireCurrentGrant(token);
        } catch (HttpClientErrorException e) {
            response.sendError(e.getStatusCode().value() == 403 ? 403 : 401, "Tenant grant is no longer valid."); return;
        } catch (Exception e) {
            response.sendError(503, "Authorization service unavailable."); return;
        }
        Set<String> permissions = new HashSet<>();
        if (c.get("permissions") instanceof Collection<?> values) values.forEach(v -> permissions.add(String.valueOf(v)));
        Number id = c.get("id", Number.class);
        request.setAttribute(ATTR, new UcpPrincipal(id == null ? 0 : id.longValue(), c.getSubject(), UUID.fromString(c.get("tenant_id", String.class)), Set.copyOf(permissions)));
        chain.doFilter(request, response);
    }
}

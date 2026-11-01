package com.ecommerce.order.accounting.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Authenticates the accounting admin API itself instead of trusting gateway-injected headers.
 *
 * - A tenant token must be issued for the ACCOUNTING application (issuer, audience, jti, tenant claims) and its grant must
 *   still be active in the identity service; its tenant scopes every query.
 * - A platform token (no application_code) keeps working for the existing single-tenant console and is pinned to the legacy
 *   tenant.
 * In both cases the X-User-* / X-Tenant-ID headers the controllers read are replaced with the verified claims, so a request
 * that reaches the service without the gateway cannot forge a role, permission or tenant.
 */
@Component
@RequiredArgsConstructor
public class AccountingAuthenticationFilter extends OncePerRequestFilter {
    public static final String PREFIX = "/api/accounting/admin";
    private static final Set<String> IDENTITY_HEADERS = Set.of("x-user-id", "x-user-email", "x-user-role", "x-user-roles",
            "x-user-permissions", "x-tenant-id", "x-application-code");

    private final AccountingJwtVerifier jwtVerifier;
    private final AccountingAuthorizationClient authorizationClient;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "An Accounting or platform token is required.");
            return;
        }
        String token = authorization.substring(7);
        Claims claims = jwtVerifier.verify(token);
        if (claims == null || claims.get("id", Number.class) == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A valid token is required.");
            return;
        }
        UUID tenant;
        String role;
        if (claims.get("application_code") != null) {
            if (!jwtVerifier.isTenantAccountingToken(claims)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "A tenant token for the Accounting application is required.");
                return;
            }
            try {
                authorizationClient.requireCurrentGrant(token);
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value() == 403 ? 403 : 401;
                response.sendError(status, status == 403 ? "Accounting access is no longer active." : "Accounting authorization could not be verified.");
                return;
            } catch (RestClientException | AccountingAuthorizationClient.AuthorizationUnavailableException e) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Accounting authorization is temporarily unavailable.");
                return;
            }
            tenant = UUID.fromString(claims.get("tenant_id", String.class));
            role = claims.get("tenant_role", String.class);
        } else {
            tenant = AccountingTenantContext.LEGACY_TENANT_ID;
            role = claims.get("role") == null ? "" : String.valueOf(claims.get("role"));
        }

        Map<String, String> verified = new HashMap<>();
        verified.put("x-user-id", String.valueOf(claims.get("id", Number.class).longValue()));
        verified.put("x-user-email", claims.getSubject() == null ? "" : claims.getSubject());
        verified.put("x-user-role", role);
        verified.put("x-user-roles", role);
        verified.put("x-user-permissions", permissions(claims));
        verified.put("x-tenant-id", tenant.toString());
        verified.put("x-application-code", claims.get("application_code") == null ? "" : String.valueOf(claims.get("application_code")));

        AccountingTenantContext.set(tenant);
        try {
            chain.doFilter(new VerifiedHeadersRequest(request, verified), response);
        } finally {
            AccountingTenantContext.clear();
        }
    }

    private static String permissions(Claims claims) {
        Object raw = claims.get("permissions");
        if (raw instanceof Collection<?> values) return String.join(",", values.stream().map(String::valueOf).toList());
        return raw == null ? "" : String.valueOf(raw);
    }

    private static final class VerifiedHeadersRequest extends HttpServletRequestWrapper {
        private final Map<String, String> verified;

        VerifiedHeadersRequest(HttpServletRequest request, Map<String, String> verified) {
            super(request);
            this.verified = verified;
        }

        @Override
        public String getHeader(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            return IDENTITY_HEADERS.contains(key) ? verified.get(key) : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String key = name.toLowerCase(Locale.ROOT);
            return IDENTITY_HEADERS.contains(key) ? Collections.enumeration(List.of(verified.get(key))) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Enumeration<String> e = super.getHeaderNames(); e.hasMoreElements(); ) {
                String name = e.nextElement();
                if (!IDENTITY_HEADERS.contains(name.toLowerCase(Locale.ROOT))) names.add(name);
            }
            names.addAll(verified.keySet());
            return Collections.enumeration(names);
        }
    }
}

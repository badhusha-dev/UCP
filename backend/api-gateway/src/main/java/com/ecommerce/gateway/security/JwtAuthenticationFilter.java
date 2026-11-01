package com.ecommerce.gateway.security;

import io.jsonwebtoken.Claims;
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

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Predicate;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // Matched as exact paths or path prefixes (never substrings): "/api/x/swagger" must not skip authentication.
    private static final List<String> OPEN_API_ENDPOINTS = List.of(
            "/api/users/login",
            "/api/users/register",
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars",
            "/actuator",
            "/fallback"
    );

    // Per-service OpenAPI documents routed as /<service>-service/v3/api-docs (see config-repo/api-gateway.yml).
    private static final Pattern SERVICE_API_DOCS = Pattern.compile("^/[a-z-]+-service/v3/api-docs(/.*)?$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        boolean isAdminPath = request.getURI().getPath().contains("/admin");
        String path = request.getURI().getPath();
        if (path.startsWith("/api/users/internal/")) {
            return onError(exchange, "Not found", HttpStatus.NOT_FOUND);
        }
        // Inventory data is tenant-owned; no inventory route is anonymous during the tenant rollout.
        boolean isPublicInventory = false;
        boolean isPublicStock = !isAdminPath && path.startsWith("/api/stock") && (
                HttpMethod.GET.equals(request.getMethod()) ||
                (HttpMethod.POST.equals(request.getMethod()) && path.endsWith("/batch")) ||
                (HttpMethod.POST.equals(request.getMethod()) && path.contains("/alerts"))
        );
        boolean isPublicProductGet = !isAdminPath && path.startsWith("/api/products") &&
                HttpMethod.GET.equals(request.getMethod());
        boolean isPublicCart = !isAdminPath && path.startsWith("/api/cart");
        boolean isPublicErrorIngest = path.startsWith("/api/errors/ingest");
        boolean isOpenEndpoint = isOpenEndpoint(path);

        boolean hasAuthHeader = request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION);

        if (!hasAuthHeader) {
            if (isPublicProductGet || isPublicInventory || isPublicStock || isPublicCart || isPublicErrorIngest || isOpenEndpoint) {
                // Strip any spoofed headers on unauthenticated / public endpoints
                ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                        .headers(h -> {
                            h.remove("X-User-Id");
                            h.remove("X-User-Email");
                            h.remove("X-User-Role");
                            h.remove("X-User-Roles");
                            h.remove("X-User-Permissions");
                            h.remove("X-Tenant-ID");
                            h.remove("X-Application-Code");
                            h.remove("X-Service-Token");
                        })
                        .build();
                return chain.filter(exchange.mutate().request(sanitizedRequest).build());
            } else {
                return onError(exchange, "Missing authorization header", HttpStatus.UNAUTHORIZED);
            }
        }

        String authHeader = request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).get(0);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader = authHeader.substring(7);
        }
        
        if (!jwtUtil.validateToken(authHeader)) {
            return onError(exchange, "Invalid authorization token", HttpStatus.UNAUTHORIZED);
        }

        try {
            Claims claims = jwtUtil.getClaims(authHeader);
            Object userId = claims.get("id");
            Object userRole = claims.get("role");
            String userEmail = claims.getSubject();

            String roleStr = userRole != null ? String.valueOf(userRole) : "";
            boolean isSuperAdmin = isPlatformSuperAdmin(claims, roleStr);
            boolean hasPermissionClaim = claims.containsKey("permissions");
            if (claims.get("application_code") != null && !TenantRoutePolicy.allows(path, userId)) {
                return onError(exchange, "This route does not accept tenant-scoped application tokens", HttpStatus.FORBIDDEN);
            }
            if (path.startsWith("/api/delivery") && !jwtUtil.validateApplicationToken(authHeader, "DELIVERY_OPS")) {
                return onError(exchange, "A tenant-scoped Delivery application token is required", HttpStatus.FORBIDDEN);
            }
            if (path.startsWith("/api/delivery")) {
                List<String> requiredPermissions = deliveryRoutePermissions(path, request.getMethod());
                if (requiredPermissions.isEmpty() || requiredPermissions.stream().noneMatch(permission -> hasPermission(claims, roleStr, permission))) {
                    return onError(exchange, "Delivery application permission is required", HttpStatus.FORBIDDEN);
                }
            }
            if (path.startsWith("/api/inventory/") && !jwtUtil.validateApplicationToken(authHeader, "INVENTORY")) {
                return onError(exchange, "An authorized tenant Inventory token is required", HttpStatus.FORBIDDEN);
            }
            if (path.startsWith("/api/platform/") && !jwtUtil.validatePlatformToken(authHeader)) {
                return onError(exchange, "A valid platform token is required", HttpStatus.UNAUTHORIZED);
            }
            if (path.startsWith("/api/crm") && !hasPermission(claims, roleStr,
                    HttpMethod.GET.equals(request.getMethod()) ? "crm:read" : "crm:manage") && !legacyStaff(roleStr, hasPermissionClaim)) {
                return onError(exchange, "CRM access requires an assigned CRM permission", HttpStatus.FORBIDDEN);
            }
            boolean ucpProcurement = path.startsWith("/api/procurement/ucp/");
            if (ucpProcurement && !jwtUtil.validateApplicationToken(authHeader, "INVENTORY")) {
                return onError(exchange, "An authorized tenant Inventory token is required", HttpStatus.FORBIDDEN);
            }
            if (path.startsWith("/api/procurement/") && !ucpProcurement && !hasPermission(claims, roleStr,
                    HttpMethod.GET.equals(request.getMethod()) ? "procurement:read" : "procurement:manage") && !legacyStaff(roleStr, hasPermissionClaim)) {
                return onError(exchange, "Procurement access requires an assigned procurement permission", HttpStatus.FORBIDDEN);
            }
            if (path.startsWith("/api/inventory/") && !path.startsWith("/api/inventory/ops/") && !hasPermission(claims, roleStr,
                    HttpMethod.GET.equals(request.getMethod()) ? "inventory:read" : "inventory:manage")) {
                return onError(exchange, "Inventory access requires an assigned tenant permission", HttpStatus.FORBIDDEN);
            }

            boolean isUserDirectoryRead = "/api/users".equals(path) && HttpMethod.GET.equals(request.getMethod());
            boolean isUserRoleOrStatusChange = path.matches("^/api/users/\\d+/(role|status)$");
            if (path.startsWith("/api/access") && !hasPermission(claims, roleStr, "roles:manage") && !legacyAdmin(roleStr, hasPermissionClaim)) {
                return onError(exchange, "Role-management permission required", HttpStatus.FORBIDDEN);
            }
            if (isUserDirectoryRead && !hasPermission(claims, roleStr, "users:read") && !legacyAdmin(roleStr, hasPermissionClaim)) {
                return onError(exchange, "User-directory permission required", HttpStatus.FORBIDDEN);
            }
            if (isUserRoleOrStatusChange && !hasPermission(claims, roleStr, "users:manage") && !legacyAdmin(roleStr, hasPermissionClaim)) {
                return onError(exchange, "User-management permission required", HttpStatus.FORBIDDEN);
            }

            Matcher userAccountPath = Pattern.compile("^/api/users/(\\d+)(?:/(.*))?$").matcher(path);
            if (userAccountPath.matches()) {
                String targetUserId = userAccountPath.group(1);
                String accountAction = userAccountPath.group(2);
                if (accountAction != null && accountAction.startsWith("loyalty/earn")
                        && !hasPermission(claims, roleStr, "loyalty:manage") && !legacyStaff(roleStr, hasPermissionClaim)) {
                    return onError(exchange, "Staff role required to award loyalty points", HttpStatus.FORBIDDEN);
                }
                boolean selfAccount = userId != null && targetUserId.equals(String.valueOf(userId));
                boolean manageAccount = accountAction != null && accountAction.matches("(role|status)")
                        && hasPermission(claims, roleStr, "users:manage");
                if (!selfAccount && !manageAccount && !hasPermission(claims, roleStr, "users:read")) {
                    return onError(exchange, "Users may only access their own account without users:read permission", HttpStatus.FORBIDDEN);
                }
            }

            // RBAC Check for Admin paths
            if (path.contains("/admin") || path.contains("/dlq") || path.startsWith("/api/platform/")) {
                String requiredPermission = routePermission(path, request.getMethod());
                if (requiredPermission == null) requiredPermission = "application:admin";
                if (path.startsWith("/api/platform/") && !isSuperAdmin) {
                    return onError(exchange, "Platform Super Admin access is required", HttpStatus.FORBIDDEN);
                }
                if (!hasPermission(claims, roleStr, requiredPermission)
                        && !legacyStaff(roleStr, hasPermissionClaim)) {
                    return onError(exchange, "Access denied: permission " + requiredPermission + " is required", HttpStatus.FORBIDDEN);
                }
            }

            // Strip client-supplied user headers and inject verified claims
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Email");
                        h.remove("X-User-Role");
                        h.remove("X-User-Roles");
                        h.remove("X-User-Permissions");
                        h.remove("X-Tenant-ID");
                        h.remove("X-Application-Code");
                        h.remove("X-Service-Token");
                    })
                    .header("X-User-Id", userId != null ? String.valueOf(userId) : "")
                    .header("X-User-Email", userEmail != null ? userEmail : "")
                    .header("X-User-Role", isSuperAdmin ? "ADMIN" : roleStr)
                    .header("X-User-Roles", roleStr)
                    .header("X-User-Permissions", permissionHeader(claims))
                    .header("X-Tenant-ID", claims.get("tenant_id", String.class) != null ? claims.get("tenant_id", String.class) : "")
                    .header("X-Application-Code", claims.get("application_code", String.class) != null ? claims.get("application_code", String.class) : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            return onError(exchange, "Failed to parse security claims: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }

    static boolean isOpenEndpoint(String path) {
        if (SERVICE_API_DOCS.matcher(path).matches()) return true;
        if (path.startsWith("/swagger-ui")) return true; // also /swagger-ui.html
        return OPEN_API_ENDPOINTS.stream().anyMatch(open -> path.equals(open) || path.startsWith(open + "/"));
    }

    private static String normalizedRole(String role) {
        return role == null ? "" : role.trim().toUpperCase().replaceFirst("^ROLE_", "");
    }

    private static String permissionHeader(Claims claims) {
        Object raw = claims.get("permissions");
        if (raw instanceof List<?> values) return String.join(",", values.stream().map(String::valueOf).toList());
        return raw == null ? "" : String.valueOf(raw);
    }

    private static boolean hasPermission(Claims claims, String role, String permission) {
        if (isPlatformSuperAdmin(claims, role)) return true;
        Object raw = claims.get("permissions");
        if (raw instanceof List<?> values) {
            return values.stream().map(String::valueOf).anyMatch(p -> p.equals("*") || p.equals(permission));
        }
        if (raw instanceof String value) {
            for (String p : value.split(",")) if (p.trim().equals("*") || p.trim().equals(permission)) return true;
        }
        return false;
    }

    private static boolean isPlatformSuperAdmin(Claims claims, String role) {
        return normalizedRole(role).equals("SUPER_ADMIN")
                && claims.get("application_code", String.class) == null;
    }

    private static boolean legacyAdmin(String role, boolean hasPermissionClaim) {
        return !hasPermissionClaim && normalizedRole(role).equals("ADMIN");
    }

    private static boolean legacyStaff(String role, boolean hasPermissionClaim) {
        return !hasPermissionClaim && Set.of("ADMIN", "MANAGER").contains(normalizedRole(role));
    }

    private static String routePermission(String path, HttpMethod method) {
        if (path.equals("/api/admin/qa") || path.startsWith("/api/admin/qa/")) {
            if (path.matches(".*/runs/[0-9a-fA-F-]+/cancel$")) return "qa:cancel";
            if (path.matches(".*/runs/[0-9a-fA-F-]+/rerun-failures$")
                    || path.endsWith("/runs") && HttpMethod.POST.equals(method)) return "qa:run";
            if (path.startsWith("/api/admin/qa/config")) return "application:admin";
            return "qa:read";
        }
        if (path.contains("/analytics") && HttpMethod.GET.equals(method)) return "analytics:read";
        if (path.startsWith("/api/accounting/admin")) {
            if (path.endsWith("/import")) return "accounting:import";
            if (path.contains("/export/")) return "accounting:export";
            if (path.matches(".*/journals/drafts/\\d+/(approve|reject)$")) return "accounting:approve";
            if (path.contains("/reverse")) return "accounting:approve";
            if (path.matches(".*/periods/\\d+/(close|reopen)$") || path.matches(".*/invoices/\\d+/status$")) return "accounting:approve";
            if (path.contains("/reconciliation") && !HttpMethod.GET.equals(method)) return "accounting:reconcile";
            return HttpMethod.GET.equals(method) ? "accounting:read" : "accounting:write";
        }
        if (path.startsWith("/api/analytics/")) return "analytics:read";
        if (path.startsWith("/api/inventory/ops/")) return "inventory:view"; // coarse gate; inventory-service enforces the exact inventory:/warehouse: permission
        if (path.startsWith("/api/procurement/ucp/")) return "purchase-order:view"; // coarse gate; procurement-service enforces exact keys
        if (path.startsWith("/api/inventory/")) return HttpMethod.GET.equals(method) ? "inventory:read" : "inventory:manage";
        if (path.startsWith("/api/stock/admin")) return HttpMethod.GET.equals(method) ? "stock:read" : "stock:manage";
        if (path.startsWith("/api/procurement/")) return HttpMethod.GET.equals(method) ? "procurement:read" : "procurement:manage";
        // Pricing & Promotions: coarse gate here; product-service enforces the exact pricing:/promotion:/coupon: permission per endpoint.
        // Order Management Control Tower: coarse gate here; order-service verifies the token itself and enforces each order:* permission.
        if (path.startsWith("/api/orders/admin/rma")) return "return:view";
        if (path.startsWith("/api/orders/admin")) return "order:view";
        if (path.startsWith("/api/products/admin/pricing")) return "pricing:read";
        if (path.startsWith("/api/products/admin/storefront")) return "storefront:read";
        if (path.startsWith("/api/products/admin")) return "catalog:manage";
        if (path.startsWith("/api/crm/")) return "crm:manage";
        if (path.startsWith("/api/loyalty/admin")) return "loyalty:manage";
        if (path.startsWith("/api/cart/admin") || path.startsWith("/api/orders/admin")) return "orders:manage";
        if (path.startsWith("/api/errors/admin") || path.contains("/dlq")) return "errors:manage";
        return null;
    }

    private static List<String> deliveryRoutePermissions(String path, HttpMethod method) {
        if (path.equals("/api/delivery/dashboard") || path.equals("/api/delivery/deliveries") && HttpMethod.GET.equals(method)
                || path.matches("^/api/delivery/deliveries/[0-9a-fA-F-]+$") && HttpMethod.GET.equals(method)
                || path.equals("/api/delivery/zones") && HttpMethod.GET.equals(method)
                || path.equals("/api/delivery/trips") && HttpMethod.GET.equals(method)) return List.of("delivery:read", "delivery:partner:self");
        if ((path.equals("/api/delivery/deliveries") || path.equals("/api/delivery/deliveries/from-order")) && HttpMethod.POST.equals(method)) return List.of("delivery:manage");
        if (path.startsWith("/api/delivery/my/")) return List.of("delivery:track:own");
        if (path.equals("/api/delivery/rules") || path.startsWith("/api/delivery/rules/")) return List.of("delivery:config");
        if (path.matches("^/api/delivery/deliveries/[0-9a-fA-F-]+/readiness$")) return List.of("delivery:manage");
        if (path.matches("^/api/delivery/deliveries/[0-9a-fA-F-]+/transitions$")) return List.of("delivery:manage", "delivery:partner:self");
        if (path.startsWith("/api/delivery/dispatch/")) return List.of("delivery:dispatch");
        if (path.startsWith("/api/delivery/assignments/")) return List.of("delivery:partner:self");
        if (path.equals("/api/delivery/partners") && HttpMethod.GET.equals(method)) return List.of("delivery:partners:read", "delivery:partner:self");
        if (path.equals("/api/delivery/partners/me") || path.equals("/api/delivery/partners/me/availability")) return List.of("delivery:partner:self");
        if (path.matches("^/api/delivery/partners/[0-9a-fA-F-]+/review$")) return List.of("delivery:partners:manage");
        if (path.equals("/api/delivery/zones") && HttpMethod.POST.equals(method)
                || path.matches("^/api/delivery/zones/[0-9a-fA-F-]+$")) return List.of("delivery:config");
        if (path.matches("^/api/delivery/deliveries/[0-9a-fA-F-]+/locations$")) return List.of("delivery:tracking");
        if (path.matches("^/api/delivery/deliveries/[0-9a-fA-F-]+/proof$")) return List.of("delivery:proof");
        if (path.equals("/api/delivery/incidents") && HttpMethod.GET.equals(method)
                || path.matches("^/api/delivery/deliveries/[0-9a-fA-F-]+/incidents$")) return List.of("delivery:incident", "delivery:partner:self");
        if (path.matches("^/api/delivery/incidents/[0-9a-fA-F-]+/resolve$")) return List.of("delivery:incident");
        if (path.equals("/api/delivery/trips") && HttpMethod.POST.equals(method)
                || path.matches("^/api/delivery/trips/[0-9a-fA-F-]+/status$")) return List.of("delivery:dispatch", "delivery:partner:self");
        if (path.equals("/api/delivery/pricing")) return List.of("delivery:config");
        if (path.equals("/api/delivery/earnings")) return List.of("delivery:report", "delivery:partner:self");
        if (path.equals("/api/delivery/reports")) return List.of("delivery:report");
        if (path.equals("/api/delivery/configuration") || path.startsWith("/api/delivery/configuration/")) return List.of("delivery:config");
        if (path.equals("/api/delivery/audit") || path.equals("/api/delivery/events")) return List.of("delivery:audit");
        return List.of();
    }

    @Override
    public int getOrder() {
        return -1; // Execute before routing
    }
}

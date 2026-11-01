package com.ecommerce.user.controller;

import com.ecommerce.user.security.JwtUtil;
import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.TenantAccessService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {
    private final TenantAccessService tenants;
    private final JwtUtil jwtUtil;
    private final UserRepository users;

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest request) {
        requireSuperAdmin(request);
        return tenants.tenants();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request, @RequestBody CreateTenantRequest body) {
        Actor actor = requireSuperAdmin(request);
        Map<String, Object> created = tenants.createTenant(body.getDisplayName(), body.getSlug(), actor.id(), actor.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{tenantId}/applications")
    public List<Map<String, Object>> applications(HttpServletRequest request, @PathVariable UUID tenantId) {
        requireSuperAdmin(request);
        return tenants.applications(tenantId);
    }

    @GetMapping("/{tenantId}/memberships")
    public List<Map<String, Object>> memberships(HttpServletRequest request, @PathVariable UUID tenantId,
                                                @RequestParam(defaultValue = "INVENTORY") String applicationCode) {
        requireSuperAdmin(request);
        return tenants.memberships(tenantId, applicationCode);
    }

    @PutMapping("/{tenantId}/applications/{applicationCode}")
    public ResponseEntity<Void> setEntitlement(HttpServletRequest request, @PathVariable UUID tenantId,
                                               @PathVariable String applicationCode, @RequestBody EntitlementRequest body) {
        Actor actor = requireSuperAdmin(request);
        tenants.setApplicationEntitlement(tenantId, applicationCode, body.isEnabled(), actor.id(), actor.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tenantId}/memberships")
    public ResponseEntity<Map<String, Object>> addMember(HttpServletRequest request, @PathVariable UUID tenantId,
                                                         @RequestBody MembershipRequest body) {
        Actor actor = requireSuperAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenants.addMembership(tenantId, body.getEmail(), actor.id(), actor.email()));
    }

    @PutMapping("/{tenantId}/memberships/{email}/applications/{applicationCode}")
    public ResponseEntity<Void> grant(HttpServletRequest request, @PathVariable UUID tenantId, @PathVariable String email,
                                      @PathVariable String applicationCode, @RequestBody GrantRequest body) {
        Actor actor = requireSuperAdmin(request);
        tenants.grantApplication(tenantId, email, applicationCode, body.getRoleKey(), actor.id(), actor.email());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tenantId}/memberships/{email}/applications/{applicationCode}")
    public ResponseEntity<Void> revoke(HttpServletRequest request, @PathVariable UUID tenantId, @PathVariable String email,
                                       @PathVariable String applicationCode) {
        Actor actor = requireSuperAdmin(request);
        tenants.revokeApplication(tenantId, email, applicationCode, actor.id(), actor.email());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tenantId}/audit")
    public List<Map<String, Object>> audit(HttpServletRequest request, @PathVariable UUID tenantId,
                                           @RequestParam(defaultValue = "50") int limit) {
        requireSuperAdmin(request);
        int bounded = Math.min(100, Math.max(1, limit));
        return tenants.auditLog(tenantId, bounded);
    }

    private Actor requireSuperAdmin(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        if (!jwtUtil.validatePlatformToken(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid platform bearer token is required.");
        Claims claims = jwtUtil.getClaims(token);
        Number id = claims.get("id", Number.class);
        User currentUser = id == null ? null : users.findById(id.longValue()).orElse(null);
        if (currentUser == null || !currentUser.isActive() || !"SUPER_ADMIN".equalsIgnoreCase(currentUser.getRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform Super Admin access is required.");
        return new Actor(id == null ? null : id.longValue(), claims.getSubject());
    }

    private record Actor(Long id, String email) {}

    @Data
    public static class CreateTenantRequest { private String displayName; private String slug; }
    @Data
    public static class EntitlementRequest { private boolean enabled; }
    @Data
    public static class MembershipRequest { private String email; }
    @Data
    public static class GrantRequest { private String roleKey; }
}

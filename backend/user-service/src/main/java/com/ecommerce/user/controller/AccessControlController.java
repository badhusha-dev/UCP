package com.ecommerce.user.controller;

import com.ecommerce.user.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class AccessControlController {
    private final RbacService rbac;

    @GetMapping("/roles")
    public List<Map<String,Object>> roles() { return rbac.roles(); }

    @GetMapping("/permissions")
    public List<Map<String,Object>> permissions() { return rbac.permissions(); }

    @GetMapping("/audit")
    public List<Map<String,Object>> audit(@RequestParam(defaultValue = "50") int limit) { return rbac.auditLog(limit); }

    @PostMapping("/roles")
    public ResponseEntity<Map<String,Object>> createRole(
            @RequestHeader(value = "X-User-Roles", required = false) String actorRole,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actorEmail,
            @RequestBody Map<String,Object> request) {
        Set<String> permissions = rbac.createRole(request, actorRole, actorId, actorEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("key", request.get("key"), "permissions", permissions));
    }

    @PutMapping("/roles/{key}/permissions")
    public ResponseEntity<Void> updatePermissions(
            @RequestHeader(value = "X-User-Roles", required = false) String actorRole,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actorEmail,
            @PathVariable String key, @RequestBody Map<String,Object> request) {
        rbac.setRolePermissions(key, request.get("permissions"), actorRole, actorId, actorEmail);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/roles/{key}")
    public ResponseEntity<Void> deleteRole(
            @RequestHeader(value = "X-User-Roles", required = false) String actorRole,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actorEmail,
            @PathVariable String key) {
        rbac.deleteRole(key, actorRole, actorId, actorEmail);
        return ResponseEntity.noContent().build();
    }
}

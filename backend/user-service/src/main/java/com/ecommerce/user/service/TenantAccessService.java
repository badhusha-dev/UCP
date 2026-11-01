package com.ecommerce.user.service;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TenantAccessService {
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])$");
    private static final Pattern APPLICATION_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,47}$");
    public static final UUID LEGACY_TENANT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ObjectMapper objectMapper;
    private final RbacService rbac;

    @Transactional
    public Map<String, Object> createTenant(String displayName, String requestedSlug, Long actorId, String actorEmail) {
        String name = displayName == null ? "" : displayName.trim();
        String slug = requestedSlug == null ? "" : requestedSlug.trim().toLowerCase(Locale.ROOT);
        if (name.isBlank() || name.length() > 160) badRequest("Client name must be between 1 and 160 characters.");
        if (!SLUG.matcher(slug).matches() || slug.equals("legacy")) badRequest("Provide a valid client slug.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE slug=?", Integer.class, slug) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A client with this slug already exists.");
        }

        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants(tenant_id,slug,display_name,status) VALUES(?,?,?,'ACTIVE')", id, slug, name);
        audit(id, actorId, actorEmail, "TENANT_CREATE", "TENANT", id.toString(), null,
                Map.of("slug", slug, "displayName", name, "status", "ACTIVE"));
        return tenant(id);
    }

    public List<Map<String, Object>> tenants() {
        return jdbc.query("SELECT tenant_id,slug,display_name,status,created_at,updated_at FROM tenants ORDER BY created_at,slug",
                (rs, row) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getObject("tenant_id", UUID.class).toString());
                    item.put("slug", rs.getString("slug"));
                    item.put("displayName", rs.getString("display_name"));
                    item.put("status", rs.getString("status"));
                    item.put("createdAt", rs.getTimestamp("created_at"));
                    item.put("updatedAt", rs.getTimestamp("updated_at"));
                    return item;
                });
    }

    public List<Map<String, Object>> applications(UUID tenantId) {
        requireTenant(tenantId);
        return jdbc.query("""
                        SELECT a.application_code,a.display_name,a.status,COALESCE(e.enabled,FALSE) enabled,
                               e.plan_code,a.approved_origins
                        FROM application_registry a
                        LEFT JOIN tenant_application_entitlements e ON e.application_code=a.application_code AND e.tenant_id=?
                        ORDER BY a.display_name
                        """, (rs, row) -> {
                    Map<String, Object> app = new LinkedHashMap<>();
                    app.put("applicationCode", rs.getString("application_code"));
                    app.put("displayName", rs.getString("display_name"));
                    app.put("status", rs.getString("status"));
                    app.put("enabled", rs.getBoolean("enabled"));
                    app.put("planCode", rs.getString("plan_code"));
                    java.sql.Array origins = rs.getArray("approved_origins");
                    app.put("approvedOrigins", origins == null ? List.of() : Arrays.asList((String[]) origins.getArray()));
                    return app;
                }, tenantId);
    }

    public List<Map<String, Object>> memberships(UUID tenantId, String applicationCode) {
        requireTenant(tenantId);
        String app = normalizeApplicationCode(applicationCode);
        return jdbc.query("""
                        SELECT u.id,u.email,u.name,m.status membership_status,
                               COALESCE(g.active,FALSE) grant_active,ta.role_key
                        FROM tenant_memberships m
                        JOIN users u ON u.id=m.user_id
                        LEFT JOIN user_application_grants g ON g.tenant_id=m.tenant_id AND g.user_id=m.user_id AND g.application_code=?
                        LEFT JOIN tenant_role_assignments ta ON ta.tenant_id=m.tenant_id AND ta.user_id=m.user_id AND ta.application_code=?
                        WHERE m.tenant_id=?
                        ORDER BY u.email
                        """, (rs, row) -> {
                    Map<String, Object> member = new LinkedHashMap<>();
                    member.put("userId", rs.getLong("id"));
                    member.put("email", rs.getString("email"));
                    member.put("name", rs.getString("name"));
                    member.put("membershipStatus", rs.getString("membership_status"));
                    member.put("applicationCode", app);
                    member.put("grantActive", rs.getBoolean("grant_active"));
                    member.put("roleKey", rs.getString("role_key"));
                    return member;
                }, app, app, tenantId);
    }

    @Transactional
    public void setApplicationEntitlement(UUID tenantId, String applicationCode, boolean enabled, Long actorId, String actorEmail) {
        String app = normalizeApplicationCode(applicationCode);
        requireTenant(tenantId);
        requireActiveApplication(app);
        Boolean exists = jdbc.queryForObject("SELECT COUNT(*) > 0 FROM tenant_application_entitlements WHERE tenant_id=? AND application_code=?",
                Boolean.class, tenantId, app);
        if (Boolean.TRUE.equals(exists)) {
            jdbc.update("UPDATE tenant_application_entitlements SET enabled=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND application_code=?",
                    enabled, tenantId, app);
        } else {
            jdbc.update("INSERT INTO tenant_application_entitlements(tenant_id,application_code,enabled) VALUES(?,?,?)", tenantId, app, enabled);
        }
        audit(tenantId, actorId, actorEmail, enabled ? "APPLICATION_ENABLE" : "APPLICATION_DISABLE", "APPLICATION", app, null,
                Map.of("enabled", enabled));
    }

    @Transactional
    public Map<String, Object> addMembership(UUID tenantId, String email, Long actorId, String actorEmail) {
        requireTenant(tenantId);
        User user = users.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found."));
        if (!user.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Inactive users cannot be added to a client.");
        Boolean exists = jdbc.queryForObject("SELECT COUNT(*) > 0 FROM tenant_memberships WHERE tenant_id=? AND user_id=?", Boolean.class, tenantId, user.getId());
        if (Boolean.TRUE.equals(exists)) throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this client.");
        jdbc.update("INSERT INTO tenant_memberships(tenant_id,user_id,status) VALUES(?,?,'ACTIVE')", tenantId, user.getId());
        audit(tenantId, actorId, actorEmail, "MEMBERSHIP_CREATE", "USER", String.valueOf(user.getId()), null,
                Map.of("email", user.getEmail(), "status", "ACTIVE"));
        return Map.of("tenantId", tenantId.toString(), "userId", user.getId(), "email", user.getEmail(), "status", "ACTIVE");
    }

    /** Procurement permissions an Inventory grant may carry: a warehouse operator receives stock with them. */
    private static final Set<String> INVENTORY_PROCUREMENT_PERMISSIONS = Set.of("goods-receipt:manage", "purchase-order:view");

    /** Inventory and warehouse permissions (module "inventory" in V13), plus the procurement pair above. */
    private static boolean isInventoryPermission(String permission) {
        return permission.startsWith("inventory:") || permission.startsWith("warehouse:")
                || INVENTORY_PROCUREMENT_PERMISSIONS.contains(permission);
    }

    @Transactional
    public void grantApplication(UUID tenantId, String email, String applicationCode, String roleKey, Long actorId, String actorEmail) {
        String app = normalizeApplicationCode(applicationCode);
        String role = roleKey == null ? "" : roleKey.trim().toUpperCase(Locale.ROOT);
        requireTenant(tenantId);
        requireActiveApplication(app);
        User user = users.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found."));
        if (!user.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Inactive users cannot receive application access.");
        requireMembership(tenantId, user.getId());
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM tenant_application_entitlements WHERE tenant_id=? AND application_code=?",
                Boolean.class, tenantId, app))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enable this application for the client before assigning user access.");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM access_roles WHERE role_key=?", Integer.class, role) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role template.");
        }
        if ("INVENTORY".equals(app)) {
            Set<String> permissions = rbac.permissionsFor(role);
            boolean readAccess = permissions.contains("inventory:view") || permissions.contains("inventory:read");
            if (!readAccess || permissions.stream().anyMatch(permission -> !isInventoryPermission(permission))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The assigned role must include Inventory read access and contain only Inventory permissions, goods receipt and purchase-order view.");
            }
        }
        if ("DELIVERY_OPS".equals(app)) {
            Set<String> permissions = rbac.permissionsFor(role);
            boolean recipientOnly = permissions.equals(Set.of("delivery:track:own"));
            if (!recipientOnly && (!permissions.contains("delivery:read") || permissions.contains("delivery:track:own")
                    || permissions.stream().anyMatch(permission -> !permission.startsWith("delivery:")))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The assigned role must include Delivery read access and contain only Delivery permissions.");
            }
        }

        Boolean exists = jdbc.queryForObject("SELECT COUNT(*) > 0 FROM user_application_grants WHERE tenant_id=? AND user_id=? AND application_code=?",
                Boolean.class, tenantId, user.getId(), app);
        if (Boolean.TRUE.equals(exists)) {
            jdbc.update("UPDATE user_application_grants SET active=TRUE,granted_by=?,granted_at=CURRENT_TIMESTAMP,revoked_at=NULL WHERE tenant_id=? AND user_id=? AND application_code=?",
                    actorId, tenantId, user.getId(), app);
        } else {
            jdbc.update("INSERT INTO user_application_grants(tenant_id,user_id,application_code,active,granted_by) VALUES(?,?,?,TRUE,?)",
                    tenantId, user.getId(), app, actorId);
        }
        Boolean roleExists = jdbc.queryForObject("SELECT COUNT(*) > 0 FROM tenant_role_assignments WHERE tenant_id=? AND user_id=? AND application_code=?",
                Boolean.class, tenantId, user.getId(), app);
        if (Boolean.TRUE.equals(roleExists)) {
            jdbc.update("UPDATE tenant_role_assignments SET role_key=?,assigned_by=?,assigned_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND user_id=? AND application_code=?",
                    role, actorId, tenantId, user.getId(), app);
        } else {
            jdbc.update("INSERT INTO tenant_role_assignments(tenant_id,user_id,application_code,role_key,assigned_by) VALUES(?,?,?,?,?)",
                    tenantId, user.getId(), app, role, actorId);
        }
        audit(tenantId, actorId, actorEmail, "APPLICATION_GRANT", "USER_APPLICATION", user.getId() + ":" + app, null,
                Map.of("email", user.getEmail(), "applicationCode", app, "role", role, "active", true));
    }

    @Transactional
    public void revokeApplication(UUID tenantId, String email, String applicationCode, Long actorId, String actorEmail) {
        String app = normalizeApplicationCode(applicationCode);
        User user = users.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found."));
        int updated = jdbc.update("UPDATE user_application_grants SET active=FALSE,revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND user_id=? AND application_code=? AND active=TRUE",
                tenantId, user.getId(), app);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active application grant not found.");
        audit(tenantId, actorId, actorEmail, "APPLICATION_GRANT_REVOKE", "USER_APPLICATION", user.getId() + ":" + app,
                Map.of("active", true), Map.of("active", false));
    }

    public ApplicationAccess requireApplicationAccess(long userId, UUID tenantId, String applicationCode) {
        String app = normalizeApplicationCode(applicationCode);
        List<ApplicationAccess> accesses = jdbc.query("""
                        SELECT u.id,u.email,u.name,t.tenant_id,t.slug,t.display_name,a.application_code,r.role_key
                        FROM users u
                        JOIN tenant_memberships m ON m.user_id=u.id AND m.status='ACTIVE'
                        JOIN tenants t ON t.tenant_id=m.tenant_id AND t.status='ACTIVE'
                        JOIN tenant_application_entitlements e ON e.tenant_id=t.tenant_id AND e.application_code=? AND e.enabled=TRUE
                        JOIN application_registry a ON a.application_code=e.application_code AND a.status='ACTIVE'
                        JOIN user_application_grants g ON g.tenant_id=t.tenant_id AND g.user_id=u.id AND g.application_code=a.application_code AND g.active=TRUE
                        JOIN tenant_role_assignments ta ON ta.tenant_id=t.tenant_id AND ta.user_id=u.id AND ta.application_code=a.application_code
                        JOIN access_roles r ON r.role_key=ta.role_key
                        WHERE u.id=? AND t.tenant_id=? AND u.active=TRUE
                        """, (rs, row) -> new ApplicationAccess(
                        rs.getLong("id"), rs.getString("email"), rs.getString("name"),
                        rs.getObject("tenant_id", UUID.class), rs.getString("slug"), rs.getString("display_name"),
                        rs.getString("application_code"), rs.getString("role_key"), Set.of()),
                app, userId, tenantId);
        if (accesses.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Application access is not enabled for this user and client.");
        ApplicationAccess found = accesses.get(0);
        return new ApplicationAccess(found.userId(), found.email(), found.name(), found.tenantId(), found.tenantSlug(),
                found.tenantName(), found.applicationCode(), found.roleKey(), rbac.permissionsFor(found.roleKey()));
    }

    public ApplicationAccess requireApplicationAccess(long userId, String applicationCode, String tenantSlug) {
        UUID tenantId = jdbc.query("SELECT tenant_id FROM tenants WHERE slug=? AND status='ACTIVE'",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                tenantSlug == null ? "" : tenantSlug.trim().toLowerCase(Locale.ROOT));
        if (tenantId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Application access is not enabled for this user and client.");
        return requireApplicationAccess(userId, tenantId, applicationCode);
    }

    public boolean isCurrentApplicationAccess(long userId, UUID tenantId, String applicationCode, String roleKey) {
        try {
            ApplicationAccess access = requireApplicationAccess(userId, tenantId, applicationCode);
            return access.roleKey().equals(roleKey);
        } catch (ResponseStatusException denied) {
            return false;
        }
    }

    public List<Map<String, Object>> auditLog(UUID tenantId, int limit) {
        return jdbc.query("SELECT id,actor_user_id AS \"actorUserId\",actor_email AS \"actorEmail\",action,target_type AS \"targetType\",target_id AS \"targetId\",before_value AS \"beforeValue\",after_value AS \"afterValue\",created_at AS \"createdAt\" FROM tenant_access_audit_log WHERE tenant_id=? ORDER BY created_at DESC LIMIT ?",
                (rs, row) -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", rs.getLong("id"));
                    entry.put("actorUserId", rs.getObject("actorUserId"));
                    entry.put("actorEmail", rs.getString("actorEmail"));
                    entry.put("action", rs.getString("action"));
                    entry.put("targetType", rs.getString("targetType"));
                    entry.put("targetId", rs.getString("targetId"));
                    entry.put("beforeValue", rs.getObject("beforeValue"));
                    entry.put("afterValue", rs.getObject("afterValue"));
                    entry.put("createdAt", rs.getTimestamp("createdAt"));
                    return entry;
                }, tenantId, limit);
    }

    private Map<String, Object> tenant(UUID id) {
        return jdbc.queryForObject("SELECT tenant_id,slug,display_name,status,created_at,updated_at FROM tenants WHERE tenant_id=?", (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getObject("tenant_id", UUID.class).toString());
            item.put("slug", rs.getString("slug"));
            item.put("displayName", rs.getString("display_name"));
            item.put("status", rs.getString("status"));
            item.put("createdAt", rs.getTimestamp("created_at"));
            item.put("updatedAt", rs.getTimestamp("updated_at"));
            return item;
        }, id);
    }

    private void requireTenant(UUID tenantId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tenants WHERE tenant_id=? AND status='ACTIVE'", Integer.class, tenantId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found or inactive.");
    }

    private void requireMembership(UUID tenantId, long userId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tenant_memberships WHERE tenant_id=? AND user_id=? AND status='ACTIVE'",
                Integer.class, tenantId, userId) == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Add the user as an active client member before granting access.");
    }

    private void requireActiveApplication(String app) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM application_registry WHERE application_code=? AND status='ACTIVE'", Integer.class, app) == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This application is not currently available.");
    }

    private static String normalizeApplicationCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!APPLICATION_CODE.matcher(normalized).matches()) badRequest("Invalid application code.");
        return normalized;
    }

    private void audit(UUID tenantId, Long actorId, String actorEmail, String action, String type, String target,
                       Object before, Object after) {
        jdbc.update("INSERT INTO tenant_access_audit_log(tenant_id,actor_user_id,actor_email,action,target_type,target_id,before_value,after_value) VALUES(?,?,?,?,?,?,?::jsonb,?::jsonb)",
                tenantId, actorId, actorEmail, action, type, target, json(before), json(after));
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize access audit details", e); }
    }

    private static void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public record ApplicationAccess(long userId, String email, String name, UUID tenantId, String tenantSlug,
                                    String tenantName, String applicationCode, String roleKey, Set<String> permissions) {}
}

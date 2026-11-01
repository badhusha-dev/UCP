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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RbacService {
    private static final Pattern ROLE_KEY = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public Set<String> permissionsFor(String role) {
        if (isSuperAdmin(role)) return Set.of("*");
        if (role == null || role.isBlank()) return Set.of();
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT permission_key FROM access_role_permissions WHERE role_key=? ORDER BY permission_key", String.class, normalize(role)));
    }

    public List<Map<String, Object>> roles() {
        return jdbc.query("SELECT r.role_key, r.display_name, r.hierarchy_level, r.system_role, r.created_at, " +
                        "COALESCE(array_agg(rp.permission_key) FILTER (WHERE rp.permission_key IS NOT NULL), ARRAY[]::varchar[]) permissions " +
                        "FROM access_roles r LEFT JOIN access_role_permissions rp ON rp.role_key=r.role_key " +
                        "GROUP BY r.role_key ORDER BY r.hierarchy_level DESC, r.role_key",
                (rs, row) -> {
                    Map<String,Object> result = new LinkedHashMap<>();
                    result.put("key", rs.getString("role_key")); result.put("name", rs.getString("display_name"));
                    result.put("hierarchyLevel", rs.getInt("hierarchy_level")); result.put("systemRole", rs.getBoolean("system_role"));
                    result.put("createdAt", rs.getTimestamp("created_at"));
                    result.put("permissions", Arrays.asList((String[]) rs.getArray("permissions").getArray()));
                    return result;
                });
    }

    public List<Map<String,Object>> permissions() {
        return jdbc.query("SELECT permission_key AS key,module_name AS module,description FROM access_permissions ORDER BY module_name,permission_key",
                (rs, row) -> Map.of("key", rs.getString("key"), "module", rs.getString("module"), "description", rs.getString("description")));
    }

    public List<Map<String,Object>> auditLog(int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbc.queryForList("SELECT id, actor_user_id AS \"actorUserId\", actor_email AS \"actorEmail\", action, target_type AS \"targetType\", target_id AS \"targetId\", before_value AS \"beforeValue\", after_value AS \"afterValue\", created_at AS \"createdAt\" FROM access_audit_log ORDER BY created_at DESC LIMIT ?", bounded);
    }

    @Transactional
    public Set<String> createRole(Map<String,Object> request, String actorRole, Long actorId, String actorEmail) {
        String key = text(request.get("key")).toUpperCase(Locale.ROOT);
        String name = text(request.get("name")).trim();
        int level = integer(request.get("hierarchyLevel"), -1);
        if (!ROLE_KEY.matcher(key).matches() || key.equals("SUPER_ADMIN")) badRequest("Use a valid role key. SUPER_ADMIN is reserved.");
        if (name.isBlank()) badRequest("Role name is required.");
        if (roleExists(key)) conflict("Role already exists.");
        requireCanGrantRole(actorRole, level);
        Set<String> grants = requestedPermissions(request.get("permissions"));
        requireGrantable(actorRole, grants);
        jdbc.update("INSERT INTO access_roles(role_key,display_name,hierarchy_level,system_role) VALUES(?,?,?,FALSE)", key, name, level);
        replacePermissions(key, grants);
        audit(actorId, actorEmail, "ROLE_CREATE", "ROLE", key, null, Map.of("name", name, "hierarchyLevel", level, "permissions", grants));
        return grants;
    }

    @Transactional
    public void setRolePermissions(String key, Object requestPermissions, String actorRole, Long actorId, String actorEmail) {
        key = normalize(key);
        Map<String,Object> target = role(key);
        if (key.equals("SUPER_ADMIN")) forbidden("The Super Admin role and its full-access grant are protected.");
        requireCanManageRole(actorRole, ((Number) target.get("hierarchyLevel")).intValue());
        Set<String> grants = requestedPermissions(requestPermissions);
        requireGrantable(actorRole, grants);
        Set<String> before = permissionsFor(key);
        replacePermissions(key, grants);
        audit(actorId, actorEmail, "ROLE_PERMISSIONS_UPDATE", "ROLE", key, before, grants);
    }

    @Transactional
    public void deleteRole(String key, String actorRole, Long actorId, String actorEmail) {
        key = normalize(key);
        Map<String,Object> target = role(key);
        if (Boolean.TRUE.equals(target.get("systemRole")) || key.equals("SUPER_ADMIN")) forbidden("Built-in roles cannot be deleted.");
        requireCanManageRole(actorRole, ((Number) target.get("hierarchyLevel")).intValue());
        if (users.existsByRoleIgnoreCase(key)) conflict("Reassign users before deleting this role.");
        jdbc.update("DELETE FROM access_roles WHERE role_key=?", key);
        audit(actorId, actorEmail, "ROLE_DELETE", "ROLE", key, target, null);
    }

    @Transactional
    public User assignUserRole(User user, String nextRole, String actorRole, Long actorId, String actorEmail) {
        String requested = normalize(nextRole);
        Map<String,Object> targetRole = role(requested);
        requireCanManageUser(actorRole, user);
        requireCanManageRole(actorRole, ((Number) targetRole.get("hierarchyLevel")).intValue());
        String current = normalize(user.getRole());
        if (current.equals("SUPER_ADMIN") && !requested.equals("SUPER_ADMIN") && superAdminCount() <= 1) {
            conflict("The last Super Admin cannot be demoted.");
        }
        if (!current.equals(requested)) {
            user.setRole(requested);
            audit(actorId, actorEmail, "USER_ROLE_UPDATE", "USER", String.valueOf(user.getId()),
                    Map.of("role", current), Map.of("role", requested));
        }
        return users.save(user);
    }

    @Transactional
    public User updateUserStatus(User user, boolean active, String actorRole, Long actorId, String actorEmail) {
        requireCanManageUser(actorRole, user);
        if (normalize(user.getRole()).equals("SUPER_ADMIN") && !active && superAdminCount() <= 1) {
            conflict("The last Super Admin account cannot be deactivated.");
        }
        boolean before = user.isActive();
        user.setActive(active);
        User saved = users.save(user);
        if (before != active) audit(actorId, actorEmail, "USER_STATUS_UPDATE", "USER", String.valueOf(user.getId()),
                Map.of("active", before), Map.of("active", active));
        return saved;
    }

    public void requireCanManageUser(String actorRole, User target) {
        requireCanManageRole(actorRole, roleLevel(target.getRole()));
    }

    private void requireCanManageRole(String actorRole, int targetLevel) {
        if (isSuperAdmin(actorRole)) return;
        int actorLevel = roleLevel(actorRole);
        if (actorLevel <= targetLevel || !permissionsFor(actorRole).contains("roles:manage")) {
            forbidden("Your role cannot manage this role or user.");
        }
    }

    private void requireCanGrantRole(String actorRole, int targetLevel) {
        if (targetLevel < 0 || targetLevel > 999) badRequest("Role hierarchy level must be between 0 and 999.");
        if (isSuperAdmin(actorRole)) return;
        if (roleLevel(actorRole) <= targetLevel || !permissionsFor(actorRole).contains("roles:manage")) {
            forbidden("You may only create roles below your own hierarchy level.");
        }
    }

    private void requireGrantable(String actorRole, Set<String> grants) {
        Set<String> allowed = permissionsFor(actorRole);
        if (!allowed.contains("*") && !allowed.containsAll(grants)) {
            forbidden("You cannot grant permissions that your own role does not hold.");
        }
        if (grants.contains("*")) forbidden("Wildcard permission grants are reserved for the Super Admin role.");
        if (grants.stream().anyMatch(p -> !permissionExists(p))) badRequest("One or more requested permissions do not exist.");
    }

    private Set<String> requestedPermissions(Object raw) {
        if (!(raw instanceof Collection<?> values)) badRequest("permissions must be an array.");
        Set<String> result = new TreeSet<>();
        for (Object value : (Collection<?>) raw) {
            String permission = text(value).trim();
            if (!permission.isBlank()) result.add(permission);
        }
        return result;
    }

    private Map<String,Object> role(String key) {
        List<Map<String,Object>> rows = jdbc.query("SELECT role_key AS \"key\", display_name AS \"name\", hierarchy_level AS \"hierarchyLevel\", system_role AS \"systemRole\" FROM access_roles WHERE role_key=?",
                (rs, row) -> { Map<String,Object> map = new LinkedHashMap<>(); map.put("key",rs.getString("key")); map.put("name",rs.getString("name")); map.put("hierarchyLevel",rs.getInt("hierarchyLevel")); map.put("systemRole",rs.getBoolean("systemRole")); return map; }, key);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + key);
        return rows.get(0);
    }

    private int roleLevel(String role) { return ((Number) role(normalize(role)).get("hierarchyLevel")).intValue(); }
    private boolean roleExists(String key) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM access_roles WHERE role_key=?)", Boolean.class, key)); }
    private boolean permissionExists(String key) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM access_permissions WHERE permission_key=?)", Boolean.class, key)); }
    private long superAdminCount() { return users.countByRoleIgnoreCaseAndActiveTrue("SUPER_ADMIN"); }
    private void replacePermissions(String key, Set<String> grants) {
        jdbc.update("DELETE FROM access_role_permissions WHERE role_key=?", key);
        grants.forEach(permission -> jdbc.update("INSERT INTO access_role_permissions(role_key,permission_key) VALUES(?,?)", key, permission));
    }

    private void audit(Long actorId, String actorEmail, String action, String type, String target, Object before, Object after) {
        jdbc.update("INSERT INTO access_audit_log(actor_user_id,actor_email,action,target_type,target_id,before_value,after_value) VALUES(?,?,?,?,?,?::jsonb,?::jsonb)",
                actorId, actorEmail, action, type, target, json(before), json(after));
    }
    private String json(Object value) { try { return value == null ? null : objectMapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize access audit record", e); } }
    private static boolean isSuperAdmin(String role) { return "SUPER_ADMIN".equals(normalize(role)); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int integer(Object value, int fallback) { try { return Integer.parseInt(text(value)); } catch (Exception ignored) { return fallback; } }
    private static void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}

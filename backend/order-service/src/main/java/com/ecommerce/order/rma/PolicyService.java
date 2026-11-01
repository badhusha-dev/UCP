package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.security.AdminPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Tenant/store return policies with a reviewed lifecycle (DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED). A return remembers the
 * version it was decided under, so publishing or rolling back a policy never changes a case that was already decided.
 */
@Service
public class PolicyService {
    public record Effective(Long versionId, Long policyId, String storeId, Policy policy) {}

    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final ObjectMapper mapper;

    public PolicyService(JdbcTemplate jdbc, AdminSupport support, ObjectMapper mapper) { this.jdbc = jdbc; this.support = support; this.mapper = mapper; }

    /** The published policy for a store (falling back to the tenant-wide '*' policy, then to the platform defaults). */
    public Effective effective(UUID tenant, String store, String kind) {
        for (String scope : store == null || store.equals("*") ? List.of("*") : List.of(store, "*")) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.id AS policy_id, v.id AS version_id, v.config FROM return_policies p JOIN return_policy_versions v ON v.id=p.published_version_id "
                    + "WHERE p.tenant_id=? AND p.store_id=? AND p.kind=? AND v.status='PUBLISHED'", tenant, scope, kind);
            if (!rows.isEmpty()) return new Effective(((Number) rows.get(0).get("version_id")).longValue(), ((Number) rows.get(0).get("policy_id")).longValue(), scope, parse((String) rows.get(0).get("config")));
        }
        return new Effective(null, null, "*", Policy.defaults(kind));
    }

    /** The exact version a return was decided under (so later decisions on that case use it, not today's policy). */
    public Policy version(UUID tenant, Long versionId, String kind) {
        if (versionId == null) return Policy.defaults(kind);
        List<String> c = jdbc.queryForList("SELECT config FROM return_policy_versions WHERE id=? AND tenant_id=?", String.class, versionId, tenant);
        return c.isEmpty() ? Policy.defaults(kind) : parse(c.get(0));
    }

    private Policy parse(String config) { try { return Policy.parse(mapper, mapper.readTree(config)); } catch (Exception e) { throw new IllegalStateException("Stored policy is corrupt.", e); } }

    // ---- administration --------------------------------------------------------------------------------------------------

    public List<Map<String, Object>> list(AdminPrincipal actor) {
        List<Map<String, Object>> rows = support.rows("SELECT p.*, (SELECT MAX(version_no) FROM return_policy_versions v WHERE v.policy_id=p.id) AS latest_version_no FROM return_policies p WHERE p.tenant_id=? ORDER BY p.store_id, p.kind", actor.tenantId());
        for (Map<String, Object> r : rows) {
            Object pv = r.get("publishedVersionId");
            r.put("publishedVersionNo", pv == null ? null : jdbc.queryForObject("SELECT version_no FROM return_policy_versions WHERE id=?", Integer.class, pv));
            r.put("latestStatus", jdbc.queryForObject("SELECT status FROM return_policy_versions WHERE policy_id=? AND version_no=?", String.class, r.get("id"), r.get("latestVersionNo")));
        }
        return rows;
    }

    public Map<String, Object> defaults(String kind) {
        String k = ReturnStateMachine.CLAIM.equals(kind) ? kind : ReturnStateMachine.RETAIL;
        return Map.of("kind", k, "config", Policy.defaultConfig(mapper, k));
    }

    @Transactional
    public Map<String, Object> create(AdminPrincipal actor, String storeId, String kind, String name, JsonNode config) {
        String store = storeId == null || storeId.isBlank() ? "*" : storeId.trim();
        if (!store.equals("*") && !store.matches("[A-Za-z0-9_.-]{1,64}")) throw bad("storeId must be '*' or a store id.");
        String k = kind == null ? ReturnStateMachine.RETAIL : kind;
        if (!Set.of(ReturnStateMachine.RETAIL, ReturnStateMachine.CLAIM).contains(k)) throw bad("kind must be RETAIL_RETURN or RESTAURANT_CLAIM.");
        String n = plain(name, "name", 120);
        JsonNode normal = Policy.normalise(mapper, config == null ? Policy.defaultConfig(mapper, k) : config);
        try {
            jdbc.update("INSERT INTO return_policies(tenant_id, store_id, kind, name, created_by) VALUES (?,?,?,?,?)", actor.tenantId(), store, k, n, actor.userId());
        } catch (DuplicateKeyException e) { throw conflict("A " + k + " policy already exists for store '" + store + "'; edit its draft instead."); }
        long id = jdbc.queryForObject("SELECT id FROM return_policies WHERE tenant_id=? AND store_id=? AND kind=?", Long.class, actor.tenantId(), store, k);
        insertVersion(actor, id, 1, normal, "Initial draft");
        audit(actor, "POLICY", id, "CREATE", n + " (" + store + ")");
        return policy(actor, id);
    }

    private void insertVersion(AdminPrincipal actor, long policyId, int no, JsonNode config, String note) {
        jdbc.update("INSERT INTO return_policy_versions(tenant_id, policy_id, version_no, config, change_note, created_by) VALUES (?,?,?,?,?,?)", actor.tenantId(), policyId, no, config.toString(), note, actor.userId());
    }

    private void audit(AdminPrincipal actor, String type, Object id, String action, String detail) {
        jdbc.update("INSERT INTO return_audit_logs(tenant_id, entity_type, entity_id, action, actor_id, actor_email, detail) VALUES (?,?,?,?,?,?,?)", actor.tenantId(), type, String.valueOf(id), action, actor.userId(), actor.email(), detail);
    }

    public Map<String, Object> policy(AdminPrincipal actor, long id) {
        Map<String, Object> p = support.one("SELECT * FROM return_policies WHERE id=? AND tenant_id=?", "Policy", id, actor.tenantId());
        p.put("versions", versions(actor, id));
        return p;
    }

    public List<Map<String, Object>> versions(AdminPrincipal actor, long policyId) {
        support.one("SELECT id FROM return_policies WHERE id=? AND tenant_id=?", "Policy", policyId, actor.tenantId());
        List<Map<String, Object>> rows = support.rows("SELECT * FROM return_policy_versions WHERE policy_id=? AND tenant_id=? ORDER BY version_no DESC", policyId, actor.tenantId());
        rows.forEach(r -> { try { r.put("config", mapper.readTree((String) r.get("config"))); } catch (Exception e) { r.put("config", null); } });
        return rows;
    }

    private Map<String, Object> lockVersion(AdminPrincipal actor, long policyId, long versionId) {
        support.one("SELECT id FROM return_policies WHERE id=? AND tenant_id=? FOR UPDATE", "Policy", policyId, actor.tenantId());
        return support.one("SELECT * FROM return_policy_versions WHERE id=? AND policy_id=? AND tenant_id=? FOR UPDATE", "Policy version", versionId, policyId, actor.tenantId());
    }

    /** Edits the open DRAFT, or starts a new draft when the latest version is already published/archived. */
    @Transactional
    public Map<String, Object> saveDraft(AdminPrincipal actor, long policyId, JsonNode config, String note) {
        support.one("SELECT id FROM return_policies WHERE id=? AND tenant_id=? FOR UPDATE", "Policy", policyId, actor.tenantId());
        JsonNode normal = Policy.normalise(mapper, config);
        Map<String, Object> latest = support.rows("SELECT * FROM return_policy_versions WHERE policy_id=? AND tenant_id=? ORDER BY version_no DESC LIMIT 1", policyId, actor.tenantId()).get(0);
        String status = (String) latest.get("status");
        if (status.equals("DRAFT")) {
            jdbc.update("UPDATE return_policy_versions SET config=?, change_note=COALESCE(?, change_note), updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'", normal.toString(), note, latest.get("id"), actor.tenantId());
        } else if (status.equals("IN_REVIEW") || status.equals("APPROVED")) {
            throw conflict("Version " + latest.get("versionNo") + " is " + status + ". Withdraw it before editing.");
        } else {
            insertVersion(actor, policyId, ((Number) latest.get("versionNo")).intValue() + 1, normal, note);
        }
        audit(actor, "POLICY", policyId, "SAVE_DRAFT", note);
        return policy(actor, policyId);
    }

    @Transactional
    public Map<String, Object> submit(AdminPrincipal actor, long policyId, long versionId) {
        Map<String, Object> v = lockVersion(actor, policyId, versionId);
        if (!"DRAFT".equals(v.get("status"))) throw conflict("Only a draft can be submitted for review.");
        jdbc.update("UPDATE return_policy_versions SET status='IN_REVIEW', submitted_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", actor.userId(), versionId);
        audit(actor, "POLICY", policyId, "SUBMIT", "v" + v.get("versionNo"));
        return policy(actor, policyId);
    }

    @Transactional
    public Map<String, Object> decide(AdminPrincipal actor, long policyId, long versionId, boolean approve, String note) {
        Map<String, Object> v = lockVersion(actor, policyId, versionId);
        if (!"IN_REVIEW".equals(v.get("status"))) throw conflict("Only a version in review can be decided.");
        if (v.get("submittedBy") != null && ((Number) v.get("submittedBy")).longValue() == actor.userId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot review a policy version you submitted.");
        if (!approve && (note == null || note.isBlank())) throw bad("A reason is required to send a policy back.");
        if (approve) jdbc.update("UPDATE return_policy_versions SET status='APPROVED', approved_by=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", actor.userId(), versionId);
        else jdbc.update("UPDATE return_policy_versions SET status='DRAFT', submitted_by=NULL, change_note=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", note, versionId);
        audit(actor, "POLICY", policyId, approve ? "APPROVE" : "SEND_BACK", note);
        return policy(actor, policyId);
    }

    @Transactional
    public Map<String, Object> publish(AdminPrincipal actor, long policyId, long versionId) {
        Map<String, Object> v = lockVersion(actor, policyId, versionId);
        if (!"APPROVED".equals(v.get("status"))) throw conflict("Only an approved version can be published.");
        goLive(actor, policyId, versionId, "PUBLISH", null);
        return policy(actor, policyId);
    }

    /** Restores an archived version as a new published version. Returns already decided keep the version they were decided under. */
    @Transactional
    public Map<String, Object> rollback(AdminPrincipal actor, long policyId, long targetVersionId, String note) {
        Map<String, Object> target = lockVersion(actor, policyId, targetVersionId);
        if (!"ARCHIVED".equals(target.get("status"))) throw conflict("Only an archived version can be restored.");
        int next = jdbc.queryForObject("SELECT MAX(version_no) FROM return_policy_versions WHERE policy_id=?", Integer.class, policyId) + 1;
        jdbc.update("INSERT INTO return_policy_versions(tenant_id, policy_id, version_no, config, change_note, created_by, approved_by) VALUES (?,?,?,?,?,?,?)",
                actor.tenantId(), policyId, next, target.get("config"), note == null ? "Rollback to v" + target.get("versionNo") : note, actor.userId(), actor.userId());
        long vid = jdbc.queryForObject("SELECT id FROM return_policy_versions WHERE policy_id=? AND version_no=?", Long.class, policyId, next);
        goLive(actor, policyId, vid, "ROLLBACK", "to v" + target.get("versionNo"));
        return policy(actor, policyId);
    }

    private void goLive(AdminPrincipal actor, long policyId, long versionId, String action, String detail) {
        jdbc.update("UPDATE return_policy_versions SET status='ARCHIVED', updated_at=CURRENT_TIMESTAMP WHERE policy_id=? AND tenant_id=? AND status='PUBLISHED' AND id<>?", policyId, actor.tenantId(), versionId);
        jdbc.update("UPDATE return_policy_versions SET status='PUBLISHED', published_by=?, published_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE id=?", actor.userId(), versionId);
        jdbc.update("UPDATE return_policies SET published_version_id=? WHERE id=? AND tenant_id=?", versionId, policyId, actor.tenantId());
        audit(actor, "POLICY", policyId, action, detail);
    }

    @Transactional
    public Map<String, Object> withdraw(AdminPrincipal actor, long policyId, long versionId) {
        Map<String, Object> v = lockVersion(actor, policyId, versionId);
        if (!Set.of("IN_REVIEW", "APPROVED").contains(v.get("status"))) throw conflict("Only a version in review or approved can be withdrawn.");
        jdbc.update("UPDATE return_policy_versions SET status='DRAFT', submitted_by=NULL, approved_by=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=?", versionId);
        audit(actor, "POLICY", policyId, "WITHDRAW", "v" + v.get("versionNo"));
        return policy(actor, policyId);
    }

    private static String plain(String value, String field, int max) {
        if (value == null || value.isBlank()) throw bad(field + " is required.");
        String v = value.strip();
        if (v.length() > max || v.matches(".*[<>].*")) throw bad(field + " must be plain text of at most " + max + " characters.");
        return v;
    }
}

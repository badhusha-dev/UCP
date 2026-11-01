package com.ecommerce.delivery.rules;

import com.ecommerce.delivery.security.DeliveryPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Tenant-scoped draft / validate / publish / rollback of {@link DeliveryRules}. */
@Service
public class DeliveryRuleRegistry {
    private static final TypeReference<Map<String, Object>> RULES = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DeliveryRuleRegistry(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** The rules in force for a tenant. Anything unreadable or invalid falls back to the safe defaults. */
    public Map<String, Object> active(UUID tenantId) {
        List<String> documents = jdbc.queryForList(
                "SELECT rules FROM delivery_rule_set WHERE tenant_id=? AND status='PUBLISHED' ORDER BY version DESC LIMIT 1",
                String.class, tenantId);
        if (documents.isEmpty()) return DeliveryRules.defaults();
        try {
            Map<String, Object> published = mapper.readValue(documents.get(0), RULES);
            if (!DeliveryRules.validate(published).isEmpty()) return DeliveryRules.defaults();
            return DeliveryRules.effective(published);
        } catch (Exception unreadable) {
            return DeliveryRules.defaults();
        }
    }

    public Map<String, Object> overview(DeliveryPrincipal actor) {
        requireConfig(actor);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("effective", active(actor.tenantId()));
        result.put("published", row(actor.tenantId(), "PUBLISHED"));
        result.put("draft", row(actor.tenantId(), "DRAFT"));
        result.put("history", jdbc.queryForList("""
                SELECT version,status,rollback_of AS "rollbackOf",published_by AS "publishedBy",published_at AS "publishedAt"
                FROM delivery_rule_set WHERE tenant_id=? AND status<>'DRAFT' ORDER BY version DESC LIMIT 50
                """, actor.tenantId()));
        return result;
    }

    @Transactional
    public Map<String, Object> saveDraft(DeliveryPrincipal actor, Map<String, Object> rules) {
        requireConfig(actor);
        if (rules == null || rules.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A draft needs at least one rule.");
        String json = write(rules);
        int updated = jdbc.update("UPDATE delivery_rule_set SET rules=?,created_by=?,created_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND status='DRAFT'",
                json, actor.userId(), actor.tenantId());
        if (updated == 0) insert(actor, json, "DRAFT", null, 0);
        audit(actor, "RULES_DRAFT_SAVE", "draft", Map.of("ruleCount", rules.size()));
        return row(actor.tenantId(), "DRAFT");
    }

    public Map<String, Object> validateDraft(DeliveryPrincipal actor) {
        requireConfig(actor);
        Map<String, Object> draft = row(actor.tenantId(), "DRAFT");
        if (draft == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no draft to validate.");
        @SuppressWarnings("unchecked")
        List<String> errors = DeliveryRules.validate((Map<String, Object>) draft.get("rules"));
        return Map.of("valid", errors.isEmpty(), "errors", errors);
    }

    @Transactional
    public Map<String, Object> publishDraft(DeliveryPrincipal actor) {
        requireConfig(actor);
        Map<String, Object> draft = row(actor.tenantId(), "DRAFT");
        if (draft == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no draft to publish.");
        @SuppressWarnings("unchecked")
        List<String> errors = DeliveryRules.validate((Map<String, Object>) draft.get("rules"));
        if (!errors.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Draft is invalid: " + String.join(" ", errors));
        int version = nextVersion(actor.tenantId());
        supersedeCurrent(actor.tenantId());
        int promoted = jdbc.update("""
                UPDATE delivery_rule_set SET status='PUBLISHED',version=?,published_by=?,published_at=CURRENT_TIMESTAMP
                WHERE tenant_id=? AND status='DRAFT'
                """, version, actor.userId(), actor.tenantId());
        if (promoted == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "The draft changed concurrently.");
        audit(actor, "RULES_PUBLISH", "v" + version, Map.of("version", version));
        return row(actor.tenantId(), "PUBLISHED");
    }

    /** Republishes the content of an earlier version as a new version, so history is never rewritten. */
    @Transactional
    public Map<String, Object> rollback(DeliveryPrincipal actor, int targetVersion) {
        requireConfig(actor);
        List<String> documents = jdbc.queryForList("SELECT rules FROM delivery_rule_set WHERE tenant_id=? AND version=? AND status<>'DRAFT'",
                String.class, actor.tenantId(), targetVersion);
        if (documents.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule version not found.");
        if (!DeliveryRules.validate(read(documents.get(0))).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "That version no longer passes validation and cannot be restored.");
        }
        int version = nextVersion(actor.tenantId());
        supersedeCurrent(actor.tenantId());
        insert(actor, documents.get(0), "PUBLISHED", targetVersion, version);
        audit(actor, "RULES_ROLLBACK", "v" + version, Map.of("version", version, "restoredFrom", targetVersion));
        return row(actor.tenantId(), "PUBLISHED");
    }

    private void supersedeCurrent(UUID tenantId) {
        jdbc.update("UPDATE delivery_rule_set SET status='SUPERSEDED' WHERE tenant_id=? AND status='PUBLISHED'", tenantId);
    }

    private int nextVersion(UUID tenantId) {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM delivery_rule_set WHERE tenant_id=? AND status<>'DRAFT'",
                Integer.class, tenantId);
        return (max == null ? 0 : max) + 1;
    }

    // Drafts hold version 0 until published; the real number is assigned at publish time.
    private void insert(DeliveryPrincipal actor, String json, String status, Integer rollbackOf, int version) {
        boolean published = "PUBLISHED".equals(status);
        try {
            jdbc.update("""
                    INSERT INTO delivery_rule_set(id,tenant_id,version,status,rules,rollback_of,created_by,published_by,published_at)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), actor.tenantId(), version, status, json, rollbackOf, actor.userId(),
                    published ? actor.userId() : null, published ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rules changed concurrently. Reload and try again.");
        }
    }

    private Map<String, Object> row(UUID tenantId, String status) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT version,status,rules,rollback_of AS "rollbackOf",created_by AS "createdBy",published_by AS "publishedBy",published_at AS "publishedAt"
                FROM delivery_rule_set WHERE tenant_id=? AND status=? ORDER BY version DESC LIMIT 1
                """, tenantId, status);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        row.put("rules", read(String.valueOf(row.get("rules"))));
        return row;
    }

    private Map<String, Object> read(String json) {
        try { return mapper.readValue(json, RULES); }
        catch (Exception bad) { return Map.of(); }
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception bad) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rules could not be serialized."); }
    }

    private void audit(DeliveryPrincipal actor, String action, String resourceId, Object details) {
        jdbc.update("""
                INSERT INTO delivery_audit_event(tenant_id,actor_user_id,actor_email,actor_role,action,resource_type,resource_id,details)
                VALUES(?,?,?,?,?,'RULES',?,?)
                """, actor.tenantId(), actor.userId(), actor.email(), actor.role(), action, resourceId, write(details));
    }

    private static void requireConfig(DeliveryPrincipal actor) {
        if (actor == null || !actor.hasPermission("delivery:config")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Delivery permission required: delivery:config");
        }
    }
}

package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.ecommerce.order.admin.AdminSupport.bad;

/**
 * One place for "do this at most once". {@link #run} protects administrative requests by (tenant, Idempotency-Key); {@link #once}
 * protects inbound domain events by (tenant, source, event id). Both rely on a unique key inserted in the same transaction as the work,
 * so concurrent duplicates serialise on the database and exactly one performs the change.
 */
@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager tm) { this.jdbc = jdbc; this.mapper = mapper; this.tx = new TransactionTemplate(tm); }

    public Map<String, Object> run(AdminPrincipal actor, String key, String action, Long refId, String fingerprint, Supplier<Map<String, Object>> work) {
        if (key == null || !key.matches("[A-Za-z0-9_.:-]{8,80}")) throw bad("An Idempotency-Key header of 8-80 letters, digits or - _ . : is required for this action.");
        String hash = sha(action + "|" + refId + "|" + fingerprint);
        try {
            return tx.execute(status -> {
                jdbc.update("INSERT INTO order_admin_actions(tenant_id, idempotency_key, action, order_id, actor_id, request_hash) VALUES (?,?,?,?,?,?)",
                        actor.tenantId(), key, action, refId, actor.userId(), hash);
                Map<String, Object> result = work.get();
                jdbc.update("UPDATE order_admin_actions SET response=? WHERE tenant_id=? AND idempotency_key=?", json(result), actor.tenantId(), key);
                return result;
            });
        } catch (DuplicateKeyException e) {
            Map<String, Object> stored = jdbc.queryForMap("SELECT request_hash, response FROM order_admin_actions WHERE tenant_id=? AND idempotency_key=?", actor.tenantId(), key);
            if (!hash.equals(stored.get("request_hash"))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "That Idempotency-Key was already used for a different request.");
            Map<String, Object> replay = new LinkedHashMap<>(parse((String) stored.get("response")));
            replay.put("idempotentReplay", true);
            return replay;
        }
    }

    public Map<String, Object> once(UUID tenant, String source, String eventId, long refId, Supplier<Map<String, Object>> work) {
        try {
            return tx.execute(s -> {
                jdbc.update("INSERT INTO order_processed_events(tenant_id, source, event_id, order_id) VALUES (?,?,?,?)", tenant, source, eventId, refId);
                return work.get();
            });
        } catch (DuplicateKeyException e) {
            return Map.of("refId", refId, "duplicate", true);
        }
    }

    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Map<String, Object> parse(String s) { try { return mapper.readValue(s, new TypeReference<Map<String, Object>>() {}); } catch (Exception e) { return Map.of(); } }

    static String sha(String s) {
        try { StringBuilder sb = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b)); return sb.toString(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}

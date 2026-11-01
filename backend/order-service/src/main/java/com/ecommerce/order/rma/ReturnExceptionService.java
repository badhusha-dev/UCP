package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.security.AdminPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Exception and fraud-risk queue. Entries are signals for a human reviewer: raising one never changes the return, blocks the customer
 * or labels anyone. Each (return, category, key) is raised once while open, so repeated scans and retries do not pile up duplicates.
 */
@Service
public class ReturnExceptionService {
    public static final Set<String> CATEGORIES = Set.of("DUPLICATE_ATTEMPT", "REPEAT_RETURNER", "HIGH_VALUE", "MISSING_ITEMS", "INSPECTION_MISMATCH", "PICKUP_FAILURE", "LOST_SHIPMENT",
            "REFUND_FAILURE", "REFUND_AMOUNT_MISMATCH", "THRESHOLD_EXCEEDED", "SUSPICIOUS_PATTERN", "MANUAL_REVIEW");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final ReturnService returns;
    private final ReturnSettingsService settings;

    public ReturnExceptionService(JdbcTemplate jdbc, AdminSupport support, ReturnService returns, ReturnSettingsService settings) {
        this.jdbc = jdbc; this.support = support; this.returns = returns; this.settings = settings;
    }

    /** Idempotent: the same open signal for the same return is stored once. */
    public void raise(UUID tenant, long returnId, String category, String priority, String summary, String factors, String openKey) {
        if (!CATEGORIES.contains(category) || !PRIORITIES.contains(priority)) throw new IllegalArgumentException("Bad exception category or priority.");
        String key = openKey == null ? "-" : ReturnService.clip(openKey, 80);
        jdbc.update("INSERT INTO return_exceptions(tenant_id, return_id, category, priority, summary, risk_factors, open_key) SELECT ?,?,?,?,?,?,? WHERE NOT EXISTS "
                + "(SELECT 1 FROM return_exceptions WHERE tenant_id=? AND return_id=? AND category=? AND open_key=?)", tenant, returnId, category, priority, ReturnService.clip(summary, 255), ReturnService.clip(factors, 500), key, tenant, returnId, category, key);
    }

    /** Re-evaluates one return against the tenant thresholds. Safe to run repeatedly. */
    @Transactional
    public int scan(UUID tenant, long returnId) {
        Map<String, Object> r = support.one("SELECT * FROM order_return WHERE id=? AND tenant_id=?", "Return", returnId, tenant);
        ReturnSettingsService.Settings s = settings.get(tenant);
        int raised = 0;
        String flags = (String) r.get("riskFlags");
        if (flags != null) for (String f : flags.split(",")) {
            if (!CATEGORIES.contains(f)) continue;
            raise(tenant, returnId, f, f.equals("HIGH_VALUE") ? "HIGH" : "MEDIUM", "Flagged at request: " + f.replace('_', ' ').toLowerCase(Locale.ROOT) + ". For review only.", null, "request");
            raised++;
        }
        Integer pf = jdbc.queryForObject("SELECT COALESCE(SUM(attempts),0) FROM reverse_pickup_requests WHERE tenant_id=? AND return_id=?", Integer.class, tenant, returnId);
        if (pf != null && pf >= s.pickupFailureLimit()) { raise(tenant, returnId, "PICKUP_FAILURE", "HIGH", "Pickup failed " + pf + " times.", null, "attempts"); raised++; }
        Integer failedRefunds = jdbc.queryForObject("SELECT COUNT(*) FROM return_refunds WHERE tenant_id=? AND return_id=? AND status='FAILED'", Integer.class, tenant, returnId);
        if (failedRefunds != null && failedRefunds > 0) { raise(tenant, returnId, "REFUND_FAILURE", "HIGH", "A refund attempt failed and needs follow-up.", null, "failed"); raised++; }
        Timestamp lostBefore = Timestamp.from(Instant.now().minusSeconds(86400L * s.lostShipmentDays()));
        Integer lost = jdbc.queryForObject("SELECT COUNT(*) FROM order_return WHERE id=? AND tenant_id=? AND status IN ('PICKED_UP','IN_TRANSIT') AND status_changed_at<?", Integer.class, returnId, tenant, lostBefore);
        if (lost != null && lost > 0) { raise(tenant, returnId, "LOST_SHIPMENT", "HIGH", "In transit for more than " + s.lostShipmentDays() + " days.", null, "transit"); raised++; }
        return raised;
    }

    public Map<String, Object> list(AdminPrincipal actor, Map<String, String> q) {
        List<Object> args = new ArrayList<>(); args.add(actor.tenantId());
        StringBuilder w = new StringBuilder(" WHERE e.tenant_id=?");
        if (!actor.hasPermission("order:view-all-stores")) {
            if (actor.storeIds().isEmpty()) w.append(" AND 1=0");
            else { w.append(" AND r.store_id IN (").append(actor.storeIds().stream().map(x -> "?").collect(Collectors.joining(","))).append(")"); args.addAll(actor.storeIds()); }
        }
        for (String[] f : new String[][]{{"status", "e.status"}, {"priority", "e.priority"}, {"category", "e.category"}}) {
            String v = q.get(f[0]);
            if (v != null && !v.isBlank()) { w.append(" AND ").append(f[1]).append("=?"); args.add(v); }
        }
        if ("open".equals(q.get("scope"))) w.append(" AND e.status IN ('OPEN','ASSIGNED','ESCALATED')");
        int page = Math.max(0, parse(q.get("page"), 0)), size = Math.min(100, Math.max(1, parse(q.get("size"), 25)));
        String from = " FROM return_exceptions e JOIN order_return r ON r.id=e.return_id";
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + from + w, Integer.class, args.toArray());
        args.add(size); args.add(page * size);
        List<Map<String, Object>> rows = support.rows("SELECT e.id, e.return_id, r.rma_number, r.status AS return_status, r.store_id, e.category, e.priority, e.status, e.summary, e.risk_factors, e.assignee_id, e.created_at, e.resolved_at, e.resolution_note"
                + from + w + " ORDER BY CASE e.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, e.created_at DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("items", rows, "total", total, "page", page, "size", size);
    }

    private static int parse(String v, int d) { try { return v == null ? d : Integer.parseInt(v); } catch (NumberFormatException e) { throw bad("Invalid number: " + v); } }

    @Transactional
    public Map<String, Object> act(AdminPrincipal actor, long id, String action, Long assignee, String note) {
        Map<String, Object> e = support.one("SELECT * FROM return_exceptions WHERE id=? AND tenant_id=?", "Exception", id, actor.tenantId());
        returns.load(actor, ((Number) e.get("returnId")).longValue());
        String status = (String) e.get("status");
        if (Set.of("RESOLVED", "DISMISSED").contains(status)) throw conflict("This exception is already " + status.toLowerCase(Locale.ROOT) + ".");
        String to;
        switch (action == null ? "" : action) {
            case "ASSIGN" -> { if (assignee == null) throw bad("assigneeId is required."); to = "ASSIGNED"; }
            case "ESCALATE" -> to = "ESCALATED";
            case "RESOLVE" -> { if (note == null || note.isBlank()) throw bad("A resolution note is required."); to = "RESOLVED"; }
            case "DISMISS" -> { if (note == null || note.isBlank()) throw bad("A reason is required to dismiss."); to = "DISMISSED"; }
            default -> throw bad("action must be ASSIGN, ESCALATE, RESOLVE or DISMISS.");
        }
        boolean closing = to.equals("RESOLVED") || to.equals("DISMISSED");
        int n = jdbc.update("UPDATE return_exceptions SET status=?, assignee_id=COALESCE(?, assignee_id), escalated_at=CASE WHEN ?='ESCALATED' THEN CURRENT_TIMESTAMP ELSE escalated_at END, "
                        + "resolved_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE resolved_at END, resolution_note=COALESCE(?, resolution_note), priority=CASE WHEN ?='ESCALATED' THEN 'CRITICAL' ELSE priority END, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status=?",
                to, assignee, to, closing, note == null ? null : ReturnService.clip(note, 500), to, id, actor.tenantId(), status);
        if (n == 0) throw conflict("The exception was changed by someone else. Reload and try again.");
        jdbc.update("INSERT INTO return_exception_events(tenant_id, exception_id, action, actor_id, note) VALUES (?,?,?,?,?)", actor.tenantId(), id, action, actor.userId(), note == null ? null : ReturnService.clip(note, 500));
        returns.audit(actor.tenantId(), ((Number) e.get("returnId")).longValue(), "EXCEPTION", String.valueOf(id), action, ReturnService.Actor.of(actor), note);
        return support.one("SELECT * FROM return_exceptions WHERE id=?", "Exception", id);
    }
}

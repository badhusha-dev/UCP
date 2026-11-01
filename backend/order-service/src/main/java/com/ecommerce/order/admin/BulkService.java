package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Bulk operations are two-step: PREVIEW evaluates every order against the same rules a single change would face and stores the
 * result; EXECUTE applies only the eligible ones, re-validating each order individually (so a bulk run can never bypass the state
 * machine) and at most once per job (a status guard makes a duplicate or concurrent execute a no-op).
 */
@Service
public class BulkService {
    public static final Set<String> ACTIONS = Set.of("ASSIGN_OPERATOR", "HOLD", "RELEASE", "ADD_NOTE", "REQUEST_CANCELLATION", "SET_CARRIER", "EXPORT");

    private final OrderRepository orders;
    private final OrderAdminService admin;
    private final OrderQueryService query;
    private final OrderSettingsService settings;
    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final ObjectMapper mapper;
    private final TransactionTemplate each;

    public BulkService(OrderRepository orders, OrderAdminService admin, OrderQueryService query, OrderSettingsService settings, JdbcTemplate jdbc, AdminSupport support, ObjectMapper mapper, PlatformTransactionManager tm) {
        this.orders = orders; this.admin = admin; this.query = query; this.settings = settings; this.jdbc = jdbc; this.support = support; this.mapper = mapper;
        this.each = new TransactionTemplate(tm);
        this.each.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    private static String permissionFor(String action) {
        return switch (action) {
            case "ASSIGN_OPERATOR", "SET_CARRIER" -> "order:process"; case "HOLD" -> "order:hold"; case "RELEASE" -> "order:release";
            case "ADD_NOTE" -> "order:note:create"; case "REQUEST_CANCELLATION" -> "order:cancel"; default -> "order:export";
        };
    }

    public Map<String, Object> preview(AdminPrincipal actor, String action, List<Long> orderIds, Map<String, Object> params) {
        require(actor, "order:bulk");
        String a = action == null ? "" : action.toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(a)) throw bad("action must be one of " + new TreeSet<>(ACTIONS) + ". Arbitrary bulk status changes are not supported.");
        require(actor, permissionFor(a));
        if (orderIds == null || orderIds.isEmpty()) throw bad("Select at least one order.");
        Set<Long> unique = new LinkedHashSet<>(orderIds);
        int max = settings.effective(actor.tenantId(), "*").maxBulkSize();
        if (unique.size() > max) throw bad("At most " + max + " orders per bulk operation (tenant setting).");
        Map<String, Object> p = params == null ? Map.of() : params;
        validateParams(a, p);
        jdbc.update("INSERT INTO order_bulk_jobs(tenant_id, action, params, total_count, created_by) VALUES (?,?,?,?,?)", actor.tenantId(), a, json(p), unique.size(), actor.userId());
        long jobId = jdbc.queryForObject("SELECT MAX(id) FROM order_bulk_jobs WHERE tenant_id=? AND created_by=?", Long.class, actor.tenantId(), actor.userId());
        int eligible = 0;
        for (Long id : unique) {
            String why = eligibility(actor, id, a);
            if (why == null) eligible++;
            jdbc.update("INSERT INTO order_bulk_job_items(tenant_id, job_id, order_id, eligible, message) VALUES (?,?,?,?,?)", actor.tenantId(), jobId, id, why == null, why);
        }
        jdbc.update("UPDATE order_bulk_jobs SET eligible_count=? WHERE id=? AND tenant_id=?", eligible, jobId, actor.tenantId());
        return job(actor, jobId);
    }

    private void validateParams(String a, Map<String, Object> p) {
        switch (a) {
            case "ASSIGN_OPERATOR" -> { if (!(p.get("operatorId") instanceof Number n) || n.longValue() <= 0) throw bad("operatorId is required."); }
            case "HOLD", "REQUEST_CANCELLATION" -> { if (p.get("reason") == null || String.valueOf(p.get("reason")).isBlank()) throw bad("A reason is required."); ContentChecks.plain(String.valueOf(p.get("reason")), "reason", 500); }
            case "ADD_NOTE" -> ContentChecks.plain(String.valueOf(p.get("note")), "note", 2000);
            case "SET_CARRIER" -> ContentChecks.plain(String.valueOf(p.get("carrier")), "carrier", 64);
            case "RELEASE", "EXPORT" -> { }
            default -> throw bad("Unsupported action.");
        }
    }

    /** Null when the order qualifies; otherwise why it was skipped. The same checks run again at execution time. */
    private String eligibility(AdminPrincipal actor, long id, String action) {
        Optional<Order> found = orders.findById(id).filter(o -> o.getTenantId().equals(actor.tenantId()) && actor.canSeeStore(o.getStoreId()));
        if (found.isEmpty()) return "Order not found";
        Order o = found.get();
        switch (action) {
            case "HOLD": return OrderStateMachine.canMove(o.getOrderType(), o.getStatus(), "ON_HOLD") ? null : "Cannot be put on hold from " + o.getStatus();
            case "REQUEST_CANCELLATION": return OrderStateMachine.canMove(o.getOrderType(), o.getStatus(), "CANCELLATION_REQUESTED") ? null : "Cancellation cannot be requested from " + o.getStatus();
            case "RELEASE": return "ON_HOLD".equals(o.getStatus()) ? null : "Not on hold";
            case "ASSIGN_OPERATOR": case "ADD_NOTE": return OrderStateMachine.isTerminal(o.getStatus()) ? "Order is " + o.getStatus() : null;
            case "SET_CARRIER": return OrderStateMachine.isTerminal(o.getStatus()) || OrderStateMachine.RESTAURANT.equals(o.getOrderType()) ? "Not applicable to this order" : null;
            default: return null;
        }
    }

    public Map<String, Object> job(AdminPrincipal actor, long jobId) {
        Map<String, Object> j = support.one("SELECT * FROM order_bulk_jobs WHERE id=? AND tenant_id=?", "Bulk job", jobId, actor.tenantId());
        j.put("items", support.rows("SELECT order_id, eligible, outcome, message FROM order_bulk_job_items WHERE tenant_id=? AND job_id=? ORDER BY id", actor.tenantId(), jobId));
        return j;
    }

    /** Executes a previewed job once. A second call (retry, double click, other tab) returns the stored result instead of repeating work. */
    public Map<String, Object> execute(AdminPrincipal actor, long jobId, String key, String correlationId) {
        require(actor, "order:bulk");
        Map<String, Object> j = support.one("SELECT * FROM order_bulk_jobs WHERE id=? AND tenant_id=?", "Bulk job", jobId, actor.tenantId());
        String action = (String) j.get("action");
        require(actor, permissionFor(action));
        if (key == null || !key.matches("[A-Za-z0-9_.:-]{8,80}")) throw bad("An Idempotency-Key header of 8-80 letters, digits or - _ . : is required to execute a bulk operation.");
        int claimed = jdbc.update("UPDATE order_bulk_jobs SET status='EXECUTING', executed_by=?, executed_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='PREVIEWED'", actor.userId(), jobId, actor.tenantId());
        if (claimed == 0) {
            Map<String, Object> done = job(actor, jobId);
            done.put("alreadyExecuted", true);
            return done;
        }
        Map<String, Object> params = parse((String) j.get("params"));
        int ok = 0, failed = 0;
        StringBuilder export = "EXPORT".equals(action) ? new StringBuilder() : null;
        List<Long> exportIds = new ArrayList<>();
        for (Map<String, Object> item : support.rows("SELECT order_id, eligible FROM order_bulk_job_items WHERE tenant_id=? AND job_id=? ORDER BY id", actor.tenantId(), jobId)) {
            long orderId = ((Number) item.get("orderId")).longValue();
            if (!Boolean.TRUE.equals(item.get("eligible"))) { mark(actor, jobId, orderId, "SKIPPED", null); continue; }
            try {
                String why = eligibility(actor, orderId, action); // re-check: the order may have changed since the preview
                if (why != null) throw conflict(why);
                if ("EXPORT".equals(action)) exportIds.add(orderId);
                else each.executeWithoutResult(s -> applyOne(actor, orderId, action, params, jobId + "-" + orderId, correlationId));
                mark(actor, jobId, orderId, "DONE", null);
                ok++;
            } catch (RuntimeException e) {
                mark(actor, jobId, orderId, "FAILED", e instanceof org.springframework.web.server.ResponseStatusException r ? r.getReason() : "Unexpected error");
                failed++;
            }
        }
        jdbc.update("UPDATE order_bulk_jobs SET status='COMPLETED', success_count=?, failure_count=? WHERE id=? AND tenant_id=?", ok, failed, jobId, actor.tenantId());
        Map<String, Object> result = job(actor, jobId);
        if ("EXPORT".equals(action) && !exportIds.isEmpty()) {
            OrderQueryService.Filter byIds = new OrderQueryService.Filter(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, exportIds);
            result.put("csv", query.exportCsv(actor, byIds, null));
        }
        return result;
    }

    private void applyOne(AdminPrincipal actor, long orderId, String action, Map<String, Object> params, String subKey, String correlationId) {
        // Each order is changed through the same service methods as a single change, with a per-order idempotency key.
        String k = ("bulk-" + subKey).substring(0, Math.min(80, ("bulk-" + subKey).length()));
        switch (action) {
            case "HOLD" -> admin.transition(actor, orderId, "ON_HOLD", String.valueOf(params.get("reason")), null, k, correlationId, null);
            case "REQUEST_CANCELLATION" -> admin.transition(actor, orderId, "CANCELLATION_REQUESTED", String.valueOf(params.get("reason")), null, k, correlationId, null);
            case "RELEASE" -> admin.release(actor, orderId, "Bulk release", k, correlationId);
            case "ASSIGN_OPERATOR" -> admin.assignOperator(actor, orderId, ((Number) params.get("operatorId")).longValue());
            case "ADD_NOTE" -> admin.addNote(actor, orderId, "INTERNAL", String.valueOf(params.get("note")));
            case "SET_CARRIER" -> {
                Order o = admin.load(actor, orderId, true);
                o.setCarrier(String.valueOf(params.get("carrier")));
                orders.save(o);
                jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, actor_id, actor_email, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        o.getTenantId(), o.getId(), o.getStatus(), o.getStatus(), "SET_CARRIER", actor.userId(), actor.email(), "ADMIN", "carrier " + params.get("carrier"), correlationId);
            }
            default -> throw bad("Unsupported action.");
        }
    }

    private void mark(AdminPrincipal actor, long jobId, long orderId, String outcome, String message) {
        jdbc.update("UPDATE order_bulk_job_items SET outcome=?, message=COALESCE(?, message) WHERE tenant_id=? AND job_id=? AND order_id=?", outcome, message == null ? null : message.substring(0, Math.min(255, message.length())), actor.tenantId(), jobId, orderId);
    }

    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Map<String, Object> parse(String s) { try { return mapper.readValue(s, new TypeReference<Map<String, Object>>() {}); } catch (Exception e) { return Map.of(); } }
}

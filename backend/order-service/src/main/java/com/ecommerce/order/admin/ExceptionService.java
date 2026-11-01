package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Exception centre. Conditions are detected from order state and tenant SLA thresholds; an exception is opened once per
 * (order, category) while open, auto-resolved when the condition clears, and worked by operators (assign / escalate / resolve).
 */
@Slf4j
@Service
public class ExceptionService {
    static final Set<String> OPEN = Set.of("OPEN", "ASSIGNED", "ESCALATED");
    static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private record Detected(String category, String priority, String summary) {}

    private final OrderRepository orders;
    private final OrderSettingsService settings;
    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final TransactionTemplate tx;
    private final OrderProducerService producer;

    public ExceptionService(OrderRepository orders, OrderSettingsService settings, JdbcTemplate jdbc, AdminSupport support, PlatformTransactionManager tm, OrderProducerService producer) {
        this.orders = orders; this.settings = settings; this.jdbc = jdbc; this.support = support; this.tx = new TransactionTemplate(tm); this.producer = producer;
    }

    // ---- detection ---------------------------------------------------------------------------------------------------

    List<Detected> detect(Order o, OrderSettingsService.Settings cfg, OffsetDateTime now) {
        List<Detected> out = new ArrayList<>();
        SlaCalculator.Result sla = SlaCalculator.evaluate(o.getOrderType(), o.getStatus(), o.getStatusChangedAt(), now, cfg.sla());
        boolean breached = sla.indicator() == SlaCalculator.Indicator.BREACHED;
        long over = breached ? sla.elapsedMinutes() / Math.max(1, sla.thresholdMinutes()) : 0;
        String bumped = over >= 3 ? "CRITICAL" : over >= 2 ? "HIGH" : "MEDIUM";
        switch (o.getStatus()) {
            case "PAYMENT_FAILED" -> out.add(new Detected("PAYMENT_FAILURE", "HIGH", "Payment failed"));
            case "PENDING_INVENTORY" -> out.add(new Detected("INVENTORY_SHORTAGE", "HIGH", "Inventory could not be reserved at checkout"));
            case "FULFILLMENT_FAILED" -> { out.add(new Detected("FULFILLMENT_FAILURE", "HIGH", "Fulfillment failed")); out.add(new Detected("INVENTORY_SHORTAGE", "MEDIUM", "Fulfillment failed; check stock")); }
            case "DELIVERY_FAILED", "PICKUP_FAILED" -> {
                Integer failures = jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE tenant_id=? AND order_id=? AND to_status IN ('DELIVERY_FAILED','PICKUP_FAILED')", Integer.class, o.getTenantId(), o.getId());
                out.add(failures != null && failures >= 2 ? new Detected("REPEATED_DELIVERY_FAILURE", "CRITICAL", "Delivery has failed " + failures + " times") : new Detected("DELIVERY_FAILURE", "HIGH", "Delivery/pickup failed"));
            }
            case "CANCELLATION_REQUESTED" -> out.add(new Detected("CANCELLATION_REQUEST", "MEDIUM", "Cancellation requested; needs a decision"));
            default -> { }
        }
        if (breached) {
            String phase = sla.phase();
            switch (phase) {
                case "CONFIRMATION", "ACCEPTANCE" -> out.add(new Detected("AWAITING_CONFIRMATION", bumped, "Awaiting confirmation for " + sla.elapsedMinutes() + " min (limit " + sla.thresholdMinutes() + ")"));
                case "PROCESSING" -> out.add(new Detected("STUCK_PROCESSING", bumped, "In processing for " + sla.elapsedMinutes() + " min (limit " + sla.thresholdMinutes() + ")"));
                case "DELIVERY" -> out.add(new Detected("DELIVERY_DELAY", bumped, "In delivery for " + sla.elapsedMinutes() + " min (limit " + sla.thresholdMinutes() + ")"));
                case "REFUND" -> out.add(new Detected("REFUND_DELAY", bumped, "Refund pending for " + sla.elapsedMinutes() + " min (limit " + sla.thresholdMinutes() + ")"));
                default -> out.add(new Detected("SLA_BREACH", bumped, phase + " SLA breached: " + sla.elapsedMinutes() + " of " + sla.thresholdMinutes() + " min"));
            }
        }
        if ("FAILED".equals(o.getRefundStatus())) out.add(new Detected("REFUND_DELAY", "HIGH", "Refund failed at the payment provider"));
        return out;
    }

    /** Opens new exceptions and auto-resolves cleared ones for the tenant. Idempotent: running it twice changes nothing the second time. */
    public Map<String, Object> scan(UUID tenant) {
        int opened = 0, resolved = 0, examined = 0;
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, OrderSettingsService.Settings> cfgByStore = new HashMap<>();
        for (int page = 0; ; page++) {
            List<Order> batch = orders.findAll((root, q, cb) -> cb.and(cb.equal(root.get("tenantId"), tenant), cb.not(root.get("status").in(OrderStateMachine.TERMINAL))),
                    PageRequest.of(page, 500, Sort.by("id"))).getContent();
            for (Order o : batch) {
                examined++;
                OrderSettingsService.Settings cfg = cfgByStore.computeIfAbsent(o.getStoreId(), s -> settings.effective(tenant, s));
                List<Detected> found = detect(o, cfg, now);
                Set<String> categories = new HashSet<>();
                for (Detected d : found) {
                    categories.add(d.category());
                    if (openException(tenant, o, d)) opened++;
                }
                resolved += resolveCleared(tenant, o.getId(), categories, "Condition cleared (auto-resolved by scan)");
            }
            if (batch.size() < 500) break;
        }
        // Terminal orders can no longer have live problems.
        for (Map<String, Object> r : jdbc.queryForList("SELECT e.id, e.order_id FROM order_exceptions e JOIN orders o ON o.id=e.order_id WHERE e.tenant_id=? AND e.status IN ('OPEN','ASSIGNED','ESCALATED') AND o.status IN ('DELIVERED','CANCELLED','REFUNDED','REJECTED') AND e.category NOT IN ('REFUND_DELAY')", tenant)) {
            resolved += resolveCleared(tenant, ((Number) r.get("order_id")).longValue(), Set.of(), "Order reached a final status");
        }
        return Map.of("examined", examined, "opened", opened, "resolved", resolved);
    }

    private boolean openException(UUID tenant, Order o, Detected d) {
        try {
            return Boolean.TRUE.equals(tx.execute(s -> {
                Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM order_exceptions WHERE tenant_id=? AND order_id=? AND category=? AND open_key='OPEN'", Integer.class, tenant, o.getId(), d.category());
                if (existing != null && existing > 0) {
                    // Priority only ever rises while an exception stays open.
                    jdbc.update("UPDATE order_exceptions SET priority=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE tenant_id=? AND order_id=? AND category=? AND open_key='OPEN' AND priority<>? AND ?>(CASE priority WHEN 'LOW' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'HIGH' THEN 2 ELSE 3 END)",
                            d.priority(), tenant, o.getId(), d.category(), d.priority(), PRIORITIES.indexOf(d.priority()));
                    return false;
                }
                jdbc.update("INSERT INTO order_exceptions(tenant_id, order_id, category, priority, summary, open_key) VALUES (?,?,?,?,?,'OPEN')", tenant, o.getId(), d.category(), d.priority(), d.summary());
                long id = jdbc.queryForObject("SELECT MAX(id) FROM order_exceptions WHERE tenant_id=? AND order_id=? AND category=?", Long.class, tenant, o.getId(), d.category());
                jdbc.update("INSERT INTO order_exception_events(tenant_id, exception_id, action, note) VALUES (?,?,?,?)", tenant, id, "OPENED", d.summary());
                if (Set.of("AWAITING_CONFIRMATION", "STUCK_PROCESSING", "DELIVERY_DELAY", "SLA_BREACH", "REFUND_DELAY").contains(d.category())) {
                    try { producer.publishOrderEvent(new OrderEvent("ORDER_SLA_BREACHED", o.getId(), o.getUserId(), o.getStatus(), o.getTotalAmount(), d.summary(), "exception-" + id)); }
                    catch (RuntimeException e) { log.warn("SLA breach event for order {} could not be queued: {}", o.getId(), e.getMessage()); }
                }
                return true;
            }));
        } catch (org.springframework.dao.DuplicateKeyException race) { return false; /* another scan opened it first */ }
    }

    private int resolveCleared(UUID tenant, long orderId, Set<String> stillDetected, String note) {
        int n = 0;
        for (Map<String, Object> e : jdbc.queryForList("SELECT id, category FROM order_exceptions WHERE tenant_id=? AND order_id=? AND status IN ('OPEN','ASSIGNED','ESCALATED')", tenant, orderId)) {
            if (stillDetected.contains(e.get("category"))) continue;
            long id = ((Number) e.get("id")).longValue();
            int u = jdbc.update("UPDATE order_exceptions SET status='RESOLVED', resolved_at=CURRENT_TIMESTAMP, resolution_note=?, open_key=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=? AND tenant_id=? AND status IN ('OPEN','ASSIGNED','ESCALATED')",
                    note, "CLOSED-" + id, id, tenant);
            if (u == 1) { jdbc.update("INSERT INTO order_exception_events(tenant_id, exception_id, action, note) VALUES (?,?,?,?)", tenant, id, "AUTO_RESOLVED", note); n++; }
        }
        return n;
    }

    @Scheduled(fixedDelayString = "${orders.exceptions.scan-delay-ms:300000}", initialDelayString = "${orders.exceptions.scan-initial-delay-ms:60000}")
    public void scanAllTenants() {
        try {
            for (UUID tenant : jdbc.queryForList("SELECT DISTINCT tenant_id FROM orders WHERE status NOT IN ('DELIVERED','CANCELLED','REFUNDED','REJECTED')", UUID.class)) {
                try { scan(tenant); } catch (RuntimeException e) { log.warn("Exception scan failed for tenant {}: {}", tenant, e.getMessage()); }
            }
        } catch (RuntimeException e) { log.debug("Exception scan skipped: {}", e.getMessage()); }
    }

    // ---- reading and working exceptions ----------------------------------------------------------------------------------

    public Map<String, Object> list(AdminPrincipal actor, String status, String category, String priority, Long assigneeId, int page, int size) {
        if (size < 1 || size > 100 || page < 0) throw bad("page must be >= 0 and size 1-100.");
        StringBuilder where = new StringBuilder(" FROM order_exceptions e JOIN orders o ON o.id=e.order_id WHERE e.tenant_id=? AND o.tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.tenantId(), actor.tenantId()));
        if (status != null && !status.isBlank()) {
            if ("ACTIVE".equals(status)) where.append(" AND e.status IN ('OPEN','ASSIGNED','ESCALATED')"); else { where.append(" AND e.status=?"); args.add(status); }
        }
        if (category != null && !category.isBlank()) { where.append(" AND e.category=?"); args.add(category); }
        if (priority != null && !priority.isBlank()) { where.append(" AND e.priority=?"); args.add(priority); }
        if (assigneeId != null) { where.append(" AND e.assignee_id=?"); args.add(assigneeId); }
        if (!actor.hasPermission("order:view-all-stores")) {
            if (actor.storeIds().isEmpty()) where.append(" AND 1=0");
            else { where.append(" AND o.store_id IN (").append(String.join(",", Collections.nCopies(actor.storeIds().size(), "?"))).append(")"); args.addAll(actor.storeIds()); }
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add((long) page * size);
        List<Map<String, Object>> rows = support.rows("SELECT e.*, o.store_id, o.order_type, o.status AS order_status" + where
                + " ORDER BY CASE e.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, e.created_at DESC, e.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        rows.forEach(r -> r.put("orderNumber", orderNumber(((Number) r.get("orderId")).longValue())));
        return Map.of("content", rows, "totalElements", total, "page", page, "size", size);
    }

    public Map<String, Object> get(AdminPrincipal actor, long id) {
        Map<String, Object> e = loadException(actor, id);
        e.put("orderNumber", orderNumber(((Number) e.get("orderId")).longValue()));
        e.put("events", support.rows("SELECT action, actor_id, note, created_at FROM order_exception_events WHERE tenant_id=? AND exception_id=? ORDER BY id", actor.tenantId(), id));
        return e;
    }

    private Map<String, Object> loadException(AdminPrincipal actor, long id) {
        Map<String, Object> e = support.one("SELECT * FROM order_exceptions WHERE id=? AND tenant_id=?", "Exception", id, actor.tenantId());
        Order o = orders.findById(((Number) e.get("orderId")).longValue()).filter(x -> x.getTenantId().equals(actor.tenantId()) && actor.canSeeStore(x.getStoreId())).orElseThrow(() -> notFound("Exception"));
        e.put("storeId", o.getStoreId());
        return e;
    }

    private void change(AdminPrincipal actor, long id, String sql, String action, String note, Object... args) {
        List<Object> all = new ArrayList<>(Arrays.asList(args));
        all.add(id); all.add(actor.tenantId());
        int n = jdbc.update("UPDATE order_exceptions SET " + sql + ", updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=? AND tenant_id=? AND status IN ('OPEN','ASSIGNED','ESCALATED')", all.toArray());
        if (n != 1) throw conflict("The exception is already closed.");
        jdbc.update("INSERT INTO order_exception_events(tenant_id, exception_id, action, actor_id, note) VALUES (?,?,?,?,?)", actor.tenantId(), id, action, actor.userId(), note);
    }

    @Transactional
    public Map<String, Object> assign(AdminPrincipal actor, long id, Long assigneeId, String note) {
        require(actor, "order:exception:manage");
        loadException(actor, id);
        if (assigneeId == null || assigneeId <= 0) throw bad("assigneeId is required.");
        change(actor, id, "assignee_id=?, status=CASE WHEN status='ESCALATED' THEN 'ESCALATED' ELSE 'ASSIGNED' END", "ASSIGNED", note, assigneeId);
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> escalate(AdminPrincipal actor, long id, String note) {
        require(actor, "order:exception:manage");
        Map<String, Object> e = loadException(actor, id);
        if (note == null || note.isBlank()) throw bad("Say why this is being escalated.");
        int next = Math.min(3, PRIORITIES.indexOf((String) e.get("priority")) + 1);
        change(actor, id, "status='ESCALATED', escalated_at=CURRENT_TIMESTAMP, priority=?", "ESCALATED", note, PRIORITIES.get(next));
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> resolve(AdminPrincipal actor, long id, boolean dismiss, String note) {
        require(actor, "order:exception:manage");
        loadException(actor, id);
        if (note == null || note.isBlank()) throw bad("A resolution note is required.");
        change(actor, id, "status=?, resolved_at=CURRENT_TIMESTAMP, resolution_note=?, open_key='CLOSED-" + id + "'", dismiss ? "DISMISSED" : "RESOLVED", note, dismiss ? "DISMISSED" : "RESOLVED", note);
        return get(actor, id);
    }

    @Transactional
    public Map<String, Object> addNote(AdminPrincipal actor, long id, String note) {
        require(actor, "order:exception:manage");
        loadException(actor, id);
        if (note == null || note.isBlank() || note.length() > 500) throw bad("A note of 1-500 characters is required.");
        jdbc.update("INSERT INTO order_exception_events(tenant_id, exception_id, action, actor_id, note) VALUES (?,?,?,?,?)", actor.tenantId(), id, "NOTE", actor.userId(), note.strip());
        return get(actor, id);
    }

    public Map<String, Object> summary(AdminPrincipal actor) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String s : List.of("OPEN", "ASSIGNED", "ESCALATED", "RESOLVED"))
            out.put(s, jdbc.queryForObject("SELECT COUNT(*) FROM order_exceptions WHERE tenant_id=? AND status=?", Long.class, actor.tenantId(), s));
        return out;
    }
}

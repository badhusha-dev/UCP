package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.security.AdminPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.ecommerce.order.admin.AdminSupport.bad;

/** Dashboard, analytics and audit reads. Everything is computed from stored returns, refunds and exchanges, scoped to the caller's tenant and stores. */
@Service
public class ReturnReportService {
    private final JdbcTemplate jdbc;
    private final AdminSupport support;

    public ReturnReportService(JdbcTemplate jdbc, AdminSupport support) { this.jdbc = jdbc; this.support = support; }

    private static final class Filter {
        final StringBuilder where = new StringBuilder(" WHERE r.tenant_id=?");
        final List<Object> args = new ArrayList<>();
        Filter(AdminPrincipal a, Map<String, String> q) {
            args.add(a.tenantId());
            if (!a.hasPermission("order:view-all-stores")) {
                if (a.storeIds().isEmpty()) where.append(" AND 1=0");
                else { where.append(" AND r.store_id IN (").append(a.storeIds().stream().map(s -> "?").collect(Collectors.joining(","))).append(")"); args.addAll(a.storeIds()); }
            }
            add("r.store_id", q.get("storeId")); add("r.return_kind", q.get("kind")); add("r.status", q.get("status"));
            if (q.get("vendorId") != null && !q.get("vendorId").isBlank()) { where.append(" AND o.vendor_id=?"); args.add(q.get("vendorId")); }
            try {
                if (q.get("from") != null && !q.get("from").isBlank()) { where.append(" AND r.created_at>=?"); args.add(Timestamp.from(LocalDate.parse(q.get("from")).atStartOfDay(ZoneOffset.UTC).toInstant())); }
                if (q.get("to") != null && !q.get("to").isBlank()) { where.append(" AND r.created_at<?"); args.add(Timestamp.from(LocalDate.parse(q.get("to")).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())); }
            } catch (java.time.format.DateTimeParseException e) { throw bad("Dates must be yyyy-MM-dd."); }
        }
        private void add(String col, String v) { if (v != null && !v.isBlank()) { where.append(" AND ").append(col).append("=?"); args.add(v); } }
        Object[] a() { return args.toArray(); }
    }

    private static final String JOIN = " FROM order_return r JOIN orders o ON o.id=r.order_id";

    private long count(Filter f, String extra) {
        Long n = jdbc.queryForObject("SELECT COUNT(*)" + JOIN + f.where + extra, Long.class, f.a());
        return n == null ? 0 : n;
    }

    public Map<String, Object> dashboard(AdminPrincipal actor, Map<String, String> q) {
        Filter f = new Filter(actor, q);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalReturns", count(f, ""));
        m.put("pendingApprovals", count(f, " AND r.status IN ('REQUESTED','UNDER_REVIEW')"));
        m.put("approved", count(f, " AND r.status='APPROVED'"));
        m.put("rejected", count(f, " AND r.status='REJECTED'"));
        m.put("awaitingPickup", count(f, " AND r.return_kind='RETAIL_RETURN' AND r.status IN ('APPROVED','PICKUP_SCHEDULED','PICKUP_FAILED')"));
        m.put("inTransit", count(f, " AND r.status IN ('PICKED_UP','IN_TRANSIT')"));
        m.put("awaitingInspection", count(f, " AND r.status IN ('RECEIVED','INSPECTION_PENDING')"));
        m.put("refundsPending", count(f, " AND r.status IN ('REFUND_PENDING','REFUND_PROCESSING','REFUND_FAILED')"));
        m.put("refundsCompleted", count(f, " AND r.status IN ('REFUNDED','CLOSED') AND EXISTS (SELECT 1 FROM return_refunds x WHERE x.return_id=r.id AND x.status='COMPLETED')"));
        BigDecimal refunded = jdbc.queryForObject("SELECT COALESCE(SUM(x.final_amount),0) FROM return_refunds x JOIN order_return r ON r.id=x.return_id JOIN orders o ON o.id=r.order_id" + f.where + " AND x.status='COMPLETED'", BigDecimal.class, f.a());
        m.put("totalRefundAmount", refunded);
        m.put("exceptionsOpen", jdbc.queryForObject("SELECT COUNT(*) FROM return_exceptions e JOIN order_return r ON r.id=e.return_id JOIN orders o ON o.id=r.order_id" + f.where + " AND e.status IN ('OPEN','ASSIGNED','ESCALATED')", Long.class, f.a()));

        // return rate = orders with a (non-cancelled) return / delivered orders in the same scope and period
        Long returnedOrders = jdbc.queryForObject("SELECT COUNT(DISTINCT r.order_id)" + JOIN + f.where + " AND r.status NOT IN ('CUSTOMER_CANCELLED')", Long.class, f.a());
        Map<String, String> oq = new HashMap<>(q); oq.remove("kind"); oq.remove("status");
        Filter of = new Filter(actor, oq);
        String orderWhere = of.where.toString().replace("r.tenant_id", "o.tenant_id").replace("r.store_id", "o.store_id").replace("r.created_at", "o.created_at");
        Long delivered = jdbc.queryForObject("SELECT COUNT(*) FROM orders o" + orderWhere + " AND o.delivered_at IS NOT NULL", Long.class, of.a());
        m.put("deliveredOrders", delivered);
        m.put("returnRatePercent", delivered == null || delivered == 0 ? null : BigDecimal.valueOf(returnedOrders == null ? 0 : returnedOrders).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(delivered), 2, RoundingMode.HALF_UP));

        m.put("avgProcessingHours", avgHours(jdbc.queryForList("SELECT r.created_at AS a, r.closed_at AS b" + JOIN + f.where + " AND r.closed_at IS NOT NULL ORDER BY r.id DESC LIMIT 5000", f.a())));
        m.put("avgRefundHours", avgHours(jdbc.queryForList("SELECT x.created_at AS a, x.completed_at AS b FROM return_refunds x JOIN order_return r ON r.id=x.return_id JOIN orders o ON o.id=r.order_id" + f.where + " AND x.status='COMPLETED' ORDER BY x.id DESC LIMIT 5000", f.a())));
        m.put("topReasons", support.rows("SELECT r.reason_code, COUNT(*) AS returns" + JOIN + f.where + " GROUP BY r.reason_code ORDER BY returns DESC LIMIT 8", f.a()));
        m.put("highReturnProducts", support.rows("SELECT i.product_id, MAX(i.product_name) AS product_name, SUM(i.quantity) AS returned_quantity, COUNT(DISTINCT r.id) AS returns FROM order_return_items i JOIN order_return r ON r.id=i.return_id JOIN orders o ON o.id=r.order_id"
                + f.where + " AND r.status NOT IN ('REJECTED','CUSTOMER_CANCELLED') GROUP BY i.product_id ORDER BY returned_quantity DESC LIMIT 8", f.a()));
        return m;
    }

    private static Object avgHours(List<Map<String, Object>> rows) {
        long total = 0; int n = 0;
        for (Map<String, Object> r : rows) {
            Timestamp a = ts(r.get("a")), b = ts(r.get("b"));
            if (a == null || b == null) continue;
            total += Duration.between(a.toInstant(), b.toInstant()).toMinutes(); n++;
        }
        return n == 0 ? null : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(n * 60L), 1, RoundingMode.HALF_UP);
    }

    private static Timestamp ts(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp t) return t;
        if (o instanceof java.time.OffsetDateTime d) return Timestamp.from(d.toInstant());
        if (o instanceof java.time.LocalDateTime d) return Timestamp.valueOf(d);
        return null;
    }

    public Map<String, Object> analytics(AdminPrincipal actor, Map<String, String> q) {
        Filter f = new Filter(actor, q);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("byStatus", support.rows("SELECT r.status, COUNT(*) AS returns" + JOIN + f.where + " GROUP BY r.status ORDER BY returns DESC", f.a()));
        m.put("byReason", support.rows("SELECT r.reason_code, COUNT(*) AS returns, COALESCE(SUM(r.refund_amount),0) AS amount" + JOIN + f.where + " GROUP BY r.reason_code ORDER BY returns DESC", f.a()));
        m.put("byStore", support.rows("SELECT r.store_id, COUNT(*) AS returns, COALESCE(SUM(r.refund_amount),0) AS amount" + JOIN + f.where + " GROUP BY r.store_id ORDER BY returns DESC LIMIT 25", f.a()));
        m.put("byResolution", support.rows("SELECT r.resolution, COUNT(*) AS returns" + JOIN + f.where + " GROUP BY r.resolution", f.a()));
        m.put("byDay", support.rows("SELECT CAST(r.created_at AS DATE) AS bucket_day, COUNT(*) AS returns" + JOIN + f.where + " GROUP BY CAST(r.created_at AS DATE) ORDER BY bucket_day", f.a()));
        m.put("refundsByMethod", support.rows("SELECT x.method, x.status, COUNT(*) AS refunds, COALESCE(SUM(x.final_amount),0) AS amount FROM return_refunds x JOIN order_return r ON r.id=x.return_id JOIN orders o ON o.id=r.order_id" + f.where + " GROUP BY x.method, x.status", f.a()));
        m.put("inspectionOutcomes", support.rows("SELECT n.outcome, COUNT(*) AS inspections FROM return_inspections n JOIN order_return r ON r.id=n.return_id JOIN orders o ON o.id=r.order_id" + f.where + " GROUP BY n.outcome", f.a()));
        m.put("exchangeStatus", support.rows("SELECT e.status, COUNT(*) AS exchanges, COALESCE(SUM(e.price_difference),0) AS price_difference FROM exchange_requests e JOIN order_return r ON r.id=e.return_id JOIN orders o ON o.id=r.order_id" + f.where + " GROUP BY e.status", f.a()));
        m.put("flagged", count(f, " AND r.risk_flags IS NOT NULL"));
        m.put("autoApproved", count(f, " AND r.auto_approved=TRUE"));
        return m;
    }

    public Map<String, Object> audit(AdminPrincipal actor, Map<String, String> q) {
        List<Object> args = new ArrayList<>(); args.add(actor.tenantId());
        StringBuilder w = new StringBuilder(" WHERE a.tenant_id=?");
        if (!actor.hasPermission("order:view-all-stores")) {
            if (actor.storeIds().isEmpty()) w.append(" AND 1=0");
            else { w.append(" AND r.store_id IN (").append(actor.storeIds().stream().map(s -> "?").collect(Collectors.joining(","))).append(")"); args.addAll(actor.storeIds()); }
        }
        if (q.get("returnId") != null && q.get("returnId").matches("\\d{1,18}")) { w.append(" AND a.return_id=?"); args.add(Long.parseLong(q.get("returnId"))); }
        if (q.get("entityType") != null && !q.get("entityType").isBlank()) { w.append(" AND a.entity_type=?"); args.add(q.get("entityType")); }
        if (q.get("action") != null && !q.get("action").isBlank()) { w.append(" AND a.action=?"); args.add(q.get("action")); }
        if (q.get("actorId") != null && q.get("actorId").matches("\\d{1,18}")) { w.append(" AND a.actor_id=?"); args.add(Long.parseLong(q.get("actorId"))); }
        int page = Math.max(0, Integer.parseInt(q.getOrDefault("page", "0"))), size = Math.min(200, Math.max(1, Integer.parseInt(q.getOrDefault("size", "50"))));
        String from = " FROM return_audit_logs a LEFT JOIN order_return r ON r.id=a.return_id";
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + from + w, Integer.class, args.toArray());
        args.add(size); args.add(page * size);
        return Map.of("items", support.rows("SELECT a.id, a.return_id, r.rma_number, a.entity_type, a.entity_id, a.action, a.actor_id, a.actor_email, a.detail, a.created_at" + from + w + " ORDER BY a.id DESC LIMIT ? OFFSET ?", args.toArray()), "total", total, "page", page, "size", size);
    }
}

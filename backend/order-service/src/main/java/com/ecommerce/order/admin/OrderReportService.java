package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.bad;

/** Dashboard and report numbers, all computed from the orders table for the caller's tenant and stores. */
@Service
public class OrderReportService {
    static final int ROW_CAP = 20000;
    private static final Set<String> NOT_REVENUE = Set.of("CANCELLED", "REJECTED", "PAYMENT_FAILED");

    @PersistenceContext private EntityManager em;
    private final OrderRepository orders;
    private final OrderQueryService query;

    public OrderReportService(OrderRepository orders, OrderQueryService query) { this.orders = orders; this.query = query; }

    private OrderQueryService.Filter withDates(OrderQueryService.Filter f, LocalDate from, LocalDate to) {
        return new OrderQueryService.Filter(f.q(), f.view(), f.statuses(), f.orderType(), f.storeIds(), f.vendorId(), f.paymentStatus(), f.fulfillmentStatus(), f.deliveryStatus(), from, to, f.minAmount(), f.maxAmount(), f.operatorId(), f.slaBreached(), f.ids());
    }

    private OrderQueryService.Filter withView(OrderQueryService.Filter f, String view, List<String> statuses) {
        return new OrderQueryService.Filter(null, view, statuses, f.orderType(), f.storeIds(), f.vendorId(), f.paymentStatus(), f.fulfillmentStatus(), f.deliveryStatus(), null, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(AdminPrincipal actor, OrderQueryService.Filter f) {
        LocalDate today = LocalDate.now();
        LocalDate from = f.from() != null ? f.from() : today.minusDays(29), to = f.to() != null ? f.to() : today;
        if (to.isBefore(from)) throw bad("to must not be before from.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("range", Map.of("from", from.toString(), "to", to.toString()));
        // Time-based counts
        m.put("ordersToday", orders.count(query.spec(actor, withDates(withView(f, null, null), today, today))));
        m.put("ordersThisMonth", orders.count(query.spec(actor, withDates(withView(f, null, null), today.withDayOfMonth(1), today))));
        // Current queue sizes (state now, independent of the date range)
        m.put("newOrders", orders.count(query.spec(actor, withView(f, "NEW", null))));
        m.put("awaitingConfirmation", orders.count(query.spec(actor, withView(f, "PENDING_CONFIRMATION", null))));
        m.put("processing", orders.count(query.spec(actor, withView(f, "PROCESSING", null))));
        m.put("awaitingFulfillment", orders.count(query.spec(actor, withView(f, "READY_FOR_FULFILLMENT", null))) + orders.count(query.spec(actor, withView(f, "PARTIALLY_FULFILLED", null))));
        m.put("onHold", orders.count(query.spec(actor, withView(f, "ON_HOLD", null))));
        OrderQueryService.Filter breached = new OrderQueryService.Filter(null, null, null, f.orderType(), f.storeIds(), f.vendorId(), f.paymentStatus(), f.fulfillmentStatus(), f.deliveryStatus(), null, null, null, null, null, true, null);
        m.put("delayedBeyondSla", orders.count(query.spec(actor, breached)));
        // Outcomes within the date range
        m.put("delivered", orders.count(query.spec(actor, withDates(withView(f, "DELIVERED", null), from, to))));
        m.put("cancelled", orders.count(query.spec(actor, withDates(withView(f, "CANCELLED", null), from, to))));
        m.put("failed", orders.count(query.spec(actor, withDates(withView(f, "FAILED", null), from, to))));
        OrderQueryService.Filter ranged = withDates(withView(f, null, null), from, to);
        m.put("ordersInRange", orders.count(query.spec(actor, ranged)));
        // Money (orders carry no currency field, so values are in the tenant's single trading currency)
        m.put("grossOrderValue", sum(actor, ranged, false));
        m.put("pendingPaymentValue", sum(actor, new OrderQueryService.Filter(null, null, null, f.orderType(), f.storeIds(), f.vendorId(), "PENDING", f.fulfillmentStatus(), f.deliveryStatus(), from, to, null, null, null, null, null), true));
        // Durations
        m.putAll(averages(actor, ranged));
        m.put("definitions", Map.of(
                "queues", "Queue sizes are the current number of orders in that state, regardless of the date range.",
                "outcomes", "Delivered, cancelled and failed count orders created inside the date range.",
                "averageProcessingMinutes", "Order created to processing started.", "averageFulfillmentMinutes", "Processing started to shipped / picked up.",
                "averageDeliveryMinutes", "Shipped to delivered.", "grossOrderValue", "Sum of order totals in range excluding cancelled, rejected and payment-failed orders.",
                "pendingPaymentValue", "Sum of orders whose payment status is PENDING (orders with unknown payment status are not counted)."));
        return m;
    }

    private BigDecimal sum(AdminPrincipal actor, OrderQueryService.Filter f, boolean pendingOnly) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<Order> root = cq.from(Order.class);
        cq.select(cb.coalesce(cb.sum(root.<BigDecimal>get("totalAmount")), BigDecimal.ZERO));
        List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
        p.add(query.spec(actor, f).toPredicate(root, cq, cb));
        p.add(cb.not(root.get("status").in(NOT_REVENUE)));
        cq.where(p.toArray(new jakarta.persistence.criteria.Predicate[0]));
        return em.createQuery(cq).getSingleResult();
    }

    private Map<String, Object> averages(AdminPrincipal actor, OrderQueryService.Filter f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Order> root = cq.from(Order.class);
        cq.multiselect(root.get("createdAt").alias("created"), root.get("processingAt").alias("processing"), root.get("shippedAt").alias("shipped"), root.get("deliveredAt").alias("delivered"));
        cq.where(query.spec(actor, f).toPredicate(root, cq, cb));
        List<Tuple> rows = em.createQuery(cq).setMaxResults(ROW_CAP).getResultList();
        double[] sum = new double[3]; int[] n = new int[3];
        for (Tuple t : rows) {
            LocalDateTime created = t.get("created", LocalDateTime.class);
            OffsetDateTime processing = t.get("processing", OffsetDateTime.class), shipped = t.get("shipped", OffsetDateTime.class), delivered = t.get("delivered", OffsetDateTime.class);
            if (processing != null && created != null) { sum[0] += Duration.between(created.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(), processing).toMinutes(); n[0]++; }
            if (processing != null && shipped != null) { sum[1] += Duration.between(processing, shipped).toMinutes(); n[1]++; }
            if (shipped != null && delivered != null) { sum[2] += Duration.between(shipped, delivered).toMinutes(); n[2]++; }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("averageProcessingMinutes", n[0] == 0 ? null : Math.round(sum[0] / n[0]));
        out.put("averageFulfillmentMinutes", n[1] == 0 ? null : Math.round(sum[1] / n[1]));
        out.put("averageDeliveryMinutes", n[2] == 0 ? null : Math.round(sum[2] / n[2]));
        out.put("averageSamples", Map.of("processing", n[0], "fulfillment", n[1], "delivery", n[2]));
        out.put("truncated", rows.size() >= ROW_CAP);
        return out;
    }

    /** Grouped counts and value for reporting; groupBy = day | store | status | type | payment. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(AdminPrincipal actor, OrderQueryService.Filter f, String groupBy) {
        String g = groupBy == null ? "day" : groupBy;
        if (!Set.of("day", "store", "status", "type", "payment").contains(g)) throw bad("groupBy must be day, store, status, type or payment.");
        LocalDate to = f.to() != null ? f.to() : LocalDate.now(), from = f.from() != null ? f.from() : to.minusDays(29);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Order> root = cq.from(Order.class);
        cq.multiselect(root.get("createdAt").alias("created"), root.get("status").alias("status"), root.get("storeId").alias("store"), root.get("orderType").alias("type"),
                root.get("paymentStatus").alias("payment"), root.get("totalAmount").alias("total"));
        cq.where(query.spec(actor, withDates(f, from, to)).toPredicate(root, cq, cb));
        List<Tuple> rows = em.createQuery(cq).setMaxResults(ROW_CAP).getResultList();
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, BigDecimal> value = new TreeMap<>();
        for (Tuple t : rows) {
            String key = switch (g) {
                case "store" -> t.get("store", String.class); case "status" -> t.get("status", String.class); case "type" -> t.get("type", String.class);
                case "payment" -> t.get("payment", String.class); default -> t.get("created", LocalDateTime.class).toLocalDate().toString();
            };
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            if (!NOT_REVENUE.contains(t.get("status", String.class))) value.merge(key, t.get("total", BigDecimal.class), BigDecimal::add);
        }
        List<Map<String, Object>> series = new ArrayList<>();
        counts.forEach((k, v) -> series.add(Map.of("key", k, "orders", v[0], "value", value.getOrDefault(k, BigDecimal.ZERO))));
        return Map.of("groupBy", g, "from", from.toString(), "to", to.toString(), "series", series, "totalOrders", rows.size(), "truncated", rows.size() >= ROW_CAP);
    }
}

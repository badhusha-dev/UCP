package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/** Tenant-scoped, store-authorised, database-paginated order search used by the list, exports, dashboard and bulk preview. */
@Service
public class OrderQueryService {
    /** Everything a caller may filter by. Unknown sort fields are rejected, never passed to the database. */
    public record Filter(String q, String view, List<String> statuses, String orderType, List<String> storeIds, String vendorId, String paymentStatus,
                         String fulfillmentStatus, String deliveryStatus, LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount,
                         Long operatorId, Boolean slaBreached, List<Long> ids) {
        public static Filter empty() { return new Filter(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null); }
    }

    static final Map<String, String> SORTABLE = Map.ofEntries(Map.entry("createdAt", "createdAt"), Map.entry("id", "id"), Map.entry("orderNumber", "id"),
            Map.entry("totalAmount", "totalAmount"), Map.entry("status", "status"), Map.entry("paymentStatus", "paymentStatus"), Map.entry("fulfillmentStatus", "fulfillmentStatus"),
            Map.entry("deliveryStatus", "shippingStatus"), Map.entry("storeId", "storeId"), Map.entry("orderType", "orderType"), Map.entry("customerName", "customerName"),
            Map.entry("statusChangedAt", "statusChangedAt"));
    static final Set<String> PAYMENT = Set.of("UNKNOWN", "PENDING", "PAID", "FAILED", "REFUNDED", "PARTIALLY_REFUNDED");
    static final Set<String> FULFILLMENT = Set.of("UNFULFILLED", "ALLOCATED", "PARTIAL", "FULFILLED");

    private final OrderRepository orders;
    private final OrderSettingsService settings;

    public OrderQueryService(OrderRepository orders, OrderSettingsService settings) { this.orders = orders; this.settings = settings; }

    public Specification<Order> spec(AdminPrincipal actor, Filter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("tenantId"), actor.tenantId())); // tenant isolation: always the verified tenant
            if (!actor.hasPermission("order:view-all-stores")) {
                if (actor.storeIds().isEmpty()) p.add(cb.disjunction()); else p.add(root.get("storeId").in(actor.storeIds()));
            }
            if (f.ids() != null) p.add(f.ids().isEmpty() ? cb.disjunction() : root.get("id").in(f.ids()));
            if (f.view() != null && !f.view().isBlank()) {
                Set<String> set = OrderStateMachine.VIEWS.get(f.view().toUpperCase(Locale.ROOT));
                if (set == null) throw bad("Unknown view " + f.view() + ". Use one of " + OrderStateMachine.VIEWS.keySet() + ".");
                p.add(root.get("status").in(set));
            }
            if (f.statuses() != null && !f.statuses().isEmpty()) p.add(root.get("status").in(f.statuses()));
            if (f.orderType() != null && !f.orderType().isBlank()) {
                if (!Set.of("RETAIL", "RESTAURANT").contains(f.orderType())) throw bad("orderType must be RETAIL or RESTAURANT.");
                p.add(cb.equal(root.get("orderType"), f.orderType()));
            }
            if (f.storeIds() != null && !f.storeIds().isEmpty()) p.add(root.get("storeId").in(f.storeIds()));
            if (f.vendorId() != null && !f.vendorId().isBlank()) p.add(cb.equal(root.get("vendorId"), f.vendorId()));
            if (f.paymentStatus() != null && !f.paymentStatus().isBlank()) {
                if (!PAYMENT.contains(f.paymentStatus())) throw bad("paymentStatus must be one of " + new TreeSet<>(PAYMENT) + ".");
                p.add(cb.equal(root.get("paymentStatus"), f.paymentStatus()));
            }
            if (f.fulfillmentStatus() != null && !f.fulfillmentStatus().isBlank()) {
                if (!FULFILLMENT.contains(f.fulfillmentStatus())) throw bad("fulfillmentStatus must be one of " + new TreeSet<>(FULFILLMENT) + ".");
                p.add(cb.equal(root.get("fulfillmentStatus"), f.fulfillmentStatus()));
            }
            if (f.deliveryStatus() != null && !f.deliveryStatus().isBlank()) p.add(cb.equal(root.get("shippingStatus"), f.deliveryStatus()));
            if (f.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay()));
            if (f.to() != null) p.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay()));
            if (f.from() != null && f.to() != null && f.to().isBefore(f.from())) throw bad("to must not be before from.");
            if (f.minAmount() != null) p.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), f.minAmount()));
            if (f.maxAmount() != null) p.add(cb.lessThanOrEqualTo(root.get("totalAmount"), f.maxAmount()));
            if (f.operatorId() != null) p.add(cb.equal(root.get("assignedOperatorId"), f.operatorId()));
            if (f.q() != null && !f.q().isBlank()) {
                String q = f.q().trim();
                Long number = parseOrderNumber(q);
                List<Predicate> any = new ArrayList<>();
                if (number != null) any.add(cb.equal(root.get("id"), number));
                if (actor.hasPermission("order:customer:view")) { // customer fields are searchable only by users allowed to see them
                    String like = "%" + q.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                    any.add(cb.like(cb.lower(root.get("customerName")), like));
                    any.add(cb.like(cb.lower(root.get("customerEmail")), like));
                    any.add(cb.like(root.get("customerPhone"), "%" + q.replaceAll("[^0-9+]", "") + "%"));
                    if (q.replaceAll("[^0-9+]", "").isEmpty()) any.remove(any.size() - 1);
                }
                p.add(any.isEmpty() ? cb.disjunction() : cb.or(any.toArray(new Predicate[0])));
            }
            if (f.slaBreached() != null) {
                Predicate breached = slaBreachedPredicate(root, cb, settings.effective(actor.tenantId(), "*").sla());
                p.add(Boolean.TRUE.equals(f.slaBreached()) ? breached : cb.not(breached));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private Predicate slaBreachedPredicate(jakarta.persistence.criteria.Root<Order> root, jakarta.persistence.criteria.CriteriaBuilder cb, SlaCalculator.Thresholds t) {
        Map<String, List<String>> byPhase = new LinkedHashMap<>();
        Set<String> statuses = new TreeSet<>(OrderStateMachine.known("RETAIL"));
        statuses.addAll(OrderStateMachine.known("RESTAURANT"));
        for (String s : statuses) { String phase = SlaCalculator.phaseOf(null, s); if (phase != null) byPhase.computeIfAbsent(phase, k -> new ArrayList<>()).add(s); }
        OffsetDateTime now = OffsetDateTime.now();
        List<Predicate> ors = new ArrayList<>();
        byPhase.forEach((phase, list) -> ors.add(cb.and(root.get("status").in(list),
                cb.lessThanOrEqualTo(root.<OffsetDateTime>get("statusChangedAt"), now.minusMinutes(SlaCalculator.thresholdFor(phase, t))))));
        return cb.or(ors.toArray(new Predicate[0]));
    }

    public static Sort sort(List<String> specs) {
        if (specs == null || specs.isEmpty()) return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        List<Sort.Order> out = new ArrayList<>();
        for (String s : specs) {
            String[] parts = s.split("[:,]");
            String field = SORTABLE.get(parts[0].trim());
            if (field == null) throw bad("Cannot sort by " + parts[0] + ". Use one of " + new TreeSet<>(SORTABLE.keySet()) + ".");
            Sort.Direction d = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
            out.add(new Sort.Order(d, field));
        }
        if (out.stream().noneMatch(o -> o.getProperty().equals("id"))) out.add(new Sort.Order(Sort.Direction.DESC, "id")); // stable pagination
        return Sort.by(out);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> search(AdminPrincipal actor, Filter f, List<String> sort, int page, int size) {
        if (page < 0) throw bad("page must be 0 or more.");
        if (size < 1 || size > 100) throw bad("size must be between 1 and 100.");
        Page<Order> result = orders.findAll(spec(actor, f), PageRequest.of(page, size, sort(sort)));
        SlaCalculator.Thresholds t = settings.effective(actor.tenantId(), "*").sla();
        OffsetDateTime now = OffsetDateTime.now();
        List<Map<String, Object>> rows = result.getContent().stream().map(o -> row(actor, o, t, now)).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", rows); out.put("page", page); out.put("size", size);
        out.put("totalElements", result.getTotalElements()); out.put("totalPages", result.getTotalPages());
        return out;
    }

    public Map<String, Object> row(AdminPrincipal actor, Order o, SlaCalculator.Thresholds t, OffsetDateTime now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("orderNumber", orderNumber(o.getId())); m.put("createdAt", o.getCreatedAt());
        boolean pii = actor.hasPermission("order:customer:view");
        m.put("customer", pii ? Map.of("userId", String.valueOf(o.getUserId()), "name", String.valueOf(o.getCustomerName() == null ? "" : o.getCustomerName()),
                "email", String.valueOf(o.getCustomerEmail() == null ? "" : o.getCustomerEmail())) : Map.of("userId", "hidden", "name", "Hidden", "email", ""));
        m.put("customerHidden", !pii);
        m.put("storeId", o.getStoreId()); m.put("vendorId", o.getVendorId()); m.put("orderType", o.getOrderType());
        m.put("itemCount", o.getItems().stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum());
        m.put("totalAmount", o.getTotalAmount()); m.put("paymentStatus", o.getPaymentStatus()); m.put("fulfillmentStatus", o.getFulfillmentStatus());
        m.put("deliveryStatus", o.getShippingStatus()); m.put("status", o.getStatus()); m.put("assignedOperatorId", o.getAssignedOperatorId());
        m.put("deliveryPartner", o.getDeliveryPartnerName()); m.put("statusChangedAt", o.getStatusChangedAt()); m.put("version", o.getVersion());
        SlaCalculator.Result sla = SlaCalculator.evaluate(o.getOrderType(), o.getStatus(), o.getStatusChangedAt(), now, t);
        m.put("sla", Map.of("indicator", sla.indicator().name(), "phase", String.valueOf(sla.phase()), "elapsedMinutes", sla.elapsedMinutes(), "thresholdMinutes", sla.thresholdMinutes()));
        return m;
    }

    /** CSV of the current filter (capped), with customer columns only for callers allowed to see them. */
    @Transactional(readOnly = true)
    public String exportCsv(AdminPrincipal actor, Filter f, List<String> sort) {
        List<Order> list = orders.findAll(spec(actor, f), PageRequest.of(0, 5000, sort(sort))).getContent();
        boolean pii = actor.hasPermission("order:customer:view");
        StringBuilder sb = new StringBuilder("orderNumber,createdAt,store,vendor,type,items,total,paymentStatus,fulfillmentStatus,deliveryStatus,status" + (pii ? ",customerName,customerEmail" : "") + "\n");
        for (Order o : list) {
            sb.append(csvCell(orderNumber(o.getId()))).append(',').append(csvCell(o.getCreatedAt())).append(',').append(csvCell(o.getStoreId())).append(',').append(csvCell(o.getVendorId())).append(',')
                    .append(csvCell(o.getOrderType())).append(',').append(o.getItems().stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum()).append(',').append(csvCell(o.getTotalAmount())).append(',')
                    .append(csvCell(o.getPaymentStatus())).append(',').append(csvCell(o.getFulfillmentStatus())).append(',').append(csvCell(o.getShippingStatus())).append(',').append(csvCell(o.getStatus()));
            if (pii) sb.append(',').append(csvCell(o.getCustomerName())).append(',').append(csvCell(o.getCustomerEmail()));
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Counts per saved menu view (cheap, database-side), for the navigation badges and dashboard. */
    @Transactional(readOnly = true)
    public Map<String, Long> viewCounts(AdminPrincipal actor, Filter base) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String v : OrderStateMachine.VIEWS.keySet()) {
            Filter f = new Filter(base.q(), v, null, base.orderType(), base.storeIds(), base.vendorId(), base.paymentStatus(), base.fulfillmentStatus(), base.deliveryStatus(), base.from(), base.to(), null, null, null, null, null);
            out.put(v, orders.count(spec(actor, f)));
        }
        return out;
    }

    public static int quantity(OrderItem i) { return i.getQuantity() == null ? 0 : i.getQuantity(); }
}

package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Return and claim requests: creation with the eligibility decision, review (approve / partial approve / reject / request information),
 * notes, assignment and every status change. All status changes go through {@link #move}, which is a compare-and-set on the current
 * status, so two operators (or an operator and a Payment/Delivery callback) can never both apply a transition from the same state.
 * Every change leaves a history row (who, when, from, to, why) and an audit entry.
 */
@Slf4j
@Service
public class ReturnService {
    public record Actor(Long id, String email, String source) {
        public static Actor of(AdminPrincipal a) { return new Actor(a.userId(), a.email(), "ADMIN"); }
        public static Actor customer(long id) { return new Actor(id, null, "CUSTOMER"); }
        public static Actor system(String source) { return new Actor(null, null, source); }
    }

    public record Line(long orderItemId, int quantity) {}

    public record CreateCommand(long orderId, String reasonCode, String details, List<Line> lines, String condition, String resolution, String requestKey) {}

    private static final Set<String> RESOLUTIONS = Set.of("REFUND", "EXCHANGE", "STORE_CREDIT");
    private static final Set<String> NOT_COMMITTED = Set.of("REJECTED", "CUSTOMER_CANCELLED");

    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final OrderRepository orders;
    private final PolicyService policies;
    private final CatalogClient catalog;
    private final ReturnSettingsService settings;
    private final AttachmentService attachments;
    private final OrderProducerService producer;

    public ReturnService(JdbcTemplate jdbc, AdminSupport support, OrderRepository orders, PolicyService policies, CatalogClient catalog, ReturnSettingsService settings,
                         AttachmentService attachments, OrderProducerService producer) {
        this.jdbc = jdbc; this.support = support; this.orders = orders; this.policies = policies; this.catalog = catalog; this.settings = settings; this.attachments = attachments; this.producer = producer;
    }

    // ---- creation --------------------------------------------------------------------------------------------------------

    /**
     * @param admin    the operator creating the request on behalf of a customer, or null
     * @param customer the customer id when the customer creates it themselves, or null
     */
    @Transactional
    public Map<String, Object> create(UUID tenant, AdminPrincipal admin, Long customer, CreateCommand cmd) {
        if (cmd.lines() != null && cmd.lines().size() > 100) throw bad("Too many lines.");
        if (cmd.reasonCode() == null || !cmd.reasonCode().matches("[A-Z0-9_]{2,40}")) throw bad("A valid reasonCode is required.");
        String details = cmd.details() == null ? "" : cmd.details().strip();
        if (details.length() > 2000) throw bad("details must be at most 2000 characters.");
        String resolution = cmd.resolution() == null ? "REFUND" : cmd.resolution();
        if (!RESOLUTIONS.contains(resolution)) throw bad("resolution must be REFUND, EXCHANGE or STORE_CREDIT.");
        String key = cmd.requestKey() == null || cmd.requestKey().isBlank() ? null : cmd.requestKey();
        if (key != null && !key.matches("[A-Za-z0-9_.:-]{8,80}")) throw bad("Idempotency-Key must be 8-80 letters, digits or - _ . :");

        Order order = orders.findByIdForUpdate(cmd.orderId()).filter(o -> o.getTenantId().equals(tenant)).orElseThrow(() -> notFound("Order"));
        if (admin != null && !admin.canSeeStore(order.getStoreId())) throw notFound("Order");
        if (customer != null && !Objects.equals(order.getUserId(), customer)) throw notFound("Order");

        if (key != null) {
            List<Map<String, Object>> prior = support.rows("SELECT id, order_id FROM order_return WHERE tenant_id=? AND request_key=?", tenant, key);
            if (!prior.isEmpty()) {
                if (((Number) prior.get(0).get("orderId")).longValue() != cmd.orderId()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "That Idempotency-Key was already used for a different request.");
                Map<String, Object> replay = new LinkedHashMap<>(summary(tenant, ((Number) prior.get(0).get("id")).longValue()));
                replay.put("idempotentReplay", true);
                return replay;
            }
        }

        String kind = "RESTAURANT".equalsIgnoreCase(order.getOrderType()) ? ReturnStateMachine.CLAIM : ReturnStateMachine.RETAIL;
        if (ReturnStateMachine.CLAIM.equals(kind) && !"REFUND".equals(resolution)) throw bad("Restaurant claims can only be resolved by a refund.");
        PolicyService.Effective eff = policies.effective(tenant, order.getStoreId(), kind);
        Policy policy = eff.policy();
        if (!policy.exchangeAllowed() && "EXCHANGE".equals(resolution)) throw bad("Exchanges are not allowed by the return policy.");
        if (!policy.storeCreditAllowed() && "STORE_CREDIT".equals(resolution)) throw bad("Store credit is not allowed by the return policy.");

        Map<Long, Integer> committed = committedQuantities(cmd.orderId());
        List<Line> requestedLines = cmd.lines() == null ? List.of() : cmd.lines();
        if (requestedLines.isEmpty() && ReturnStateMachine.CLAIM.equals(kind)) // an order-level claim covers whatever is still unclaimed
            requestedLines = order.getItems().stream().filter(i -> i.getQuantity() - committed.getOrDefault(i.getId(), 0) > 0).map(i -> new Line(i.getId(), i.getQuantity() - committed.getOrDefault(i.getId(), 0))).toList();
        if (requestedLines.isEmpty()) throw bad("Select at least one item to return.");
        final List<Line> lines = requestedLines;
        boolean needsCategory = !policy.nonReturnableCategories().isEmpty() || !policy.categoryWindowDays().isEmpty();
        Map<Long, Long> categories = needsCategory ? catalog.categories(order.getItems().stream().map(OrderItem::getProductId).toList()) : Map.of();
        List<EligibilityEngine.Line> orderLines = order.getItems().stream().map(i -> new EligibilityEngine.Line(i.getId(), i.getProductId(), categories.get(i.getProductId()), i.getQuantity(),
                committed.getOrDefault(i.getId(), 0), i.getPrice())).toList();
        OffsetDateTime deliveredAt = order.getDeliveredAt();
        boolean delivered = deliveredAt != null || "DELIVERED".equals(order.getStatus());
        EligibilityEngine.OrderFacts facts = new EligibilityEngine.OrderFacts(kind, order.getOrderType(), delivered, deliveredAt, order.getCreatedAt().atOffset(ZoneOffset.UTC), order.getTotalAmount());
        EligibilityEngine.Result decision = EligibilityEngine.evaluate(policy, facts, orderLines, lines.stream().map(l -> new EligibilityEngine.Requested(l.orderItemId(), l.quantity())).toList(),
                cmd.reasonCode(), cmd.condition(), OffsetDateTime.now(ZoneOffset.UTC));
        if (!decision.eligible()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Not eligible: " + String.join(" ", decision.blockers()));

        Map<Long, OrderItem> byId = order.getItems().stream().collect(Collectors.toMap(OrderItem::getId, i -> i));
        BigDecimal estimate = decision.estimatedAmount() == null ? BigDecimal.ZERO : decision.estimatedAmount();
        List<String> flags = riskFlags(tenant, order, lines, estimate, customerOf(order, customer));

        KeyHolder kh = new GeneratedKeyHolder();
        Long ownerId = customer != null ? customer : order.getUserId();
        String decisionJson = decisionText(decision, eff);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO order_return(order_id, user_id, reason, status, refund_amount, created_at, tenant_id, store_id, return_kind, resolution, reason_code, source, request_key, policy_version_id, policy_decision, risk_flags, evidence_required, status_changed_at) "
                    + "VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", new String[]{"id"});
            int n = 1;
            ps.setLong(n++, order.getId()); ps.setLong(n++, ownerId); ps.setString(n++, details.isEmpty() ? cmd.reasonCode() : details); ps.setString(n++, "REQUESTED"); ps.setBigDecimal(n++, estimate);
            ps.setObject(n++, tenant); ps.setString(n++, order.getStoreId()); ps.setString(n++, kind); ps.setString(n++, resolution); ps.setString(n++, cmd.reasonCode());
            ps.setString(n++, admin != null ? "ADMIN" : "CUSTOMER"); ps.setString(n++, key); if (eff.versionId() == null) ps.setNull(n++, java.sql.Types.BIGINT); else ps.setLong(n++, eff.versionId());
            ps.setString(n++, decisionJson); ps.setString(n++, flags.isEmpty() ? null : String.join(",", flags)); ps.setBoolean(n, decision.evidenceRequired());
            return ps;
        }, kh);
        long id = ((Number) kh.getKeys().get("id")).longValue();
        jdbc.update("UPDATE order_return SET rma_number=? WHERE id=?", String.format("RMA-%06d", id), id);
        for (Line l : lines) {
            OrderItem oi = byId.get(l.orderItemId());
            jdbc.update("INSERT INTO order_return_items(return_id, order_item_id, product_id, product_name, quantity, unit_price, refund_amount, disposition, reason_code, condition_reported) VALUES (?,?,?,?,?,?,?,'PENDING',?,?)",
                    id, oi.getId(), oi.getProductId(), oi.getProductName(), l.quantity(), oi.getPrice(), oi.getPrice().multiply(BigDecimal.valueOf(l.quantity())).setScale(2, RoundingMode.HALF_UP), cmd.reasonCode(), cmd.condition());
        }
        Actor actor = admin != null ? Actor.of(admin) : Actor.customer(customer);
        history(tenant, id, null, "REQUESTED", "CREATE", actor, details.isEmpty() ? cmd.reasonCode() : details);
        audit(tenant, id, "RETURN", String.valueOf(id), "CREATE", actor, kind + " " + resolution + " reason=" + cmd.reasonCode() + (flags.isEmpty() ? "" : " flags=" + flags));
        publish("RETURN_REQUESTED", tenant, id, null);
        if (decision.autoApprove() && !decision.manualReview() && !decision.evidenceRequired()) {
            for (Line l : lines) jdbc.update("UPDATE order_return_items SET approved_quantity=quantity WHERE return_id=? AND order_item_id=?", id, l.orderItemId());
            jdbc.update("UPDATE order_return SET auto_approved=TRUE, approved_at=CURRENT_TIMESTAMP WHERE id=?", id);
            move(tenant, id, "APPROVED", Actor.system("POLICY"), "Auto-approved by return policy.", "AUTO_APPROVE");
        }
        return summary(tenant, id);
    }

    private Map<Long, Integer> committedQuantities(long orderId) {
        Map<Long, Integer> m = new HashMap<>();
        jdbc.query("SELECT i.order_item_id, SUM(i.quantity) AS q FROM order_return_items i JOIN order_return r ON r.id=i.return_id WHERE r.order_id=? AND r.status NOT IN ('REJECTED','CUSTOMER_CANCELLED') GROUP BY i.order_item_id",
                rs -> { m.put(rs.getLong(1), rs.getInt(2)); }, orderId);
        return m;
    }

    private static Long customerOf(Order o, Long customer) { return customer != null ? customer : o.getUserId(); }

    /** Informational flags for reviewers. They never block or reject a request on their own. */
    private List<String> riskFlags(UUID tenant, Order order, List<Line> lines, BigDecimal estimate, Long customerId) {
        List<String> flags = new ArrayList<>();
        ReturnSettingsService.Settings s = settings.get(tenant);
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM order_return r JOIN order_return_items i ON i.return_id=r.id WHERE r.order_id=? AND r.status NOT IN ('REJECTED','CUSTOMER_CANCELLED','CLOSED') AND i.order_item_id IN ("
                + lines.stream().map(l -> "?").collect(Collectors.joining(",")) + ")", Integer.class, concat(order.getId(), lines.stream().map(Line::orderItemId).toArray()));
        if (open != null && open > 0) flags.add("DUPLICATE_ATTEMPT");
        Timestamp since = Timestamp.from(Instant.now().minusSeconds(86400L * s.repeatReturnDays()));
        Integer recent = jdbc.queryForObject("SELECT COUNT(*) FROM order_return WHERE tenant_id=? AND user_id=? AND created_at>=?", Integer.class, tenant, customerId, since);
        if (recent != null && recent + 1 >= s.repeatReturnCount()) flags.add("REPEAT_RETURNER");
        if (estimate.compareTo(s.highValueRefundThreshold()) >= 0) flags.add("HIGH_VALUE");
        return flags;
    }

    private static Object[] concat(Object first, Object[] rest) { Object[] out = new Object[rest.length + 1]; out[0] = first; System.arraycopy(rest, 0, out, 1, rest.length); return out; }

    private static String decisionText(EligibilityEngine.Result d, PolicyService.Effective eff) {
        return "policyVersion=" + eff.versionId() + "; scope=" + eff.storeId() + "; evidenceRequired=" + d.evidenceRequired() + "; sellerFault=" + d.sellerFault() + "; autoApprove=" + d.autoApprove() + "; manualReview=" + d.manualReview()
                + (d.notes().isEmpty() ? "" : "; " + String.join(" ", d.notes()));
    }

    // ---- reading ---------------------------------------------------------------------------------------------------------

    /** A return of this tenant that the actor's store scope allows; anything else is "not found". */
    public Map<String, Object> load(AdminPrincipal actor, long id) {
        Map<String, Object> r = support.one("SELECT * FROM order_return WHERE id=? AND tenant_id=?", "Return", id, actor.tenantId());
        if (!actor.canSeeStore((String) r.get("storeId"))) throw notFound("Return");
        return r;
    }

    public Map<String, Object> summary(UUID tenant, long id) {
        Map<String, Object> r = support.one("SELECT * FROM order_return WHERE id=? AND tenant_id=?", "Return", id, tenant);
        return shape(r, true, null);
    }

    private Map<String, Object> shape(Map<String, Object> r, boolean customerDetails, Map<String, Object> order) {
        Map<String, Object> m = new LinkedHashMap<>(r);
        m.remove("requestKey");
        Object flags = m.get("riskFlags");
        m.put("riskFlags", flags == null || flags.toString().isBlank() ? List.of() : Arrays.asList(flags.toString().split(",")));
        m.put("orderNumber", orderNumber(((Number) r.get("orderId")).longValue()));
        m.put("allowedTransitions", ReturnStateMachine.allowed((String) r.get("returnKind"), (String) r.get("status")));
        return m;
    }

    public Map<String, Object> list(AdminPrincipal actor, Map<String, String> q) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE r.tenant_id=?");
        args.add(actor.tenantId());
        if (!actor.hasPermission("order:view-all-stores")) {
            if (actor.storeIds().isEmpty()) where.append(" AND 1=0");
            else { where.append(" AND r.store_id IN (").append(actor.storeIds().stream().map(s -> "?").collect(Collectors.joining(","))).append(")"); args.addAll(actor.storeIds()); }
        }
        String view = q.get("view");
        if (view != null && !view.isBlank()) {
            Set<String> statuses = ReturnStateMachine.VIEWS.get(view.toUpperCase(Locale.ROOT));
            if (statuses == null) throw bad("Unknown view " + view + ".");
            where.append(" AND r.status IN (").append(statuses.stream().map(s -> "?").collect(Collectors.joining(","))).append(")"); args.addAll(statuses);
        }
        eq(where, args, "r.status", q.get("status")); eq(where, args, "r.store_id", q.get("storeId")); eq(where, args, "r.return_kind", q.get("kind"));
        eq(where, args, "r.reason_code", q.get("reasonCode")); eq(where, args, "r.resolution", q.get("resolution"));
        if (q.get("vendorId") != null && !q.get("vendorId").isBlank()) { where.append(" AND o.vendor_id=?"); args.add(q.get("vendorId")); }
        if (q.get("assignedTo") != null && q.get("assignedTo").matches("\\d{1,18}")) { where.append(" AND r.assigned_operator_id=?"); args.add(Long.parseLong(q.get("assignedTo"))); }
        if ("true".equals(q.get("flagged"))) where.append(" AND r.risk_flags IS NOT NULL");
        range(where, args, q.get("from"), q.get("to"));
        String text = q.get("q");
        if (text != null && !text.isBlank()) {
            String t = text.strip();
            Long on = parseOrderNumber(t);
            where.append(" AND (UPPER(r.rma_number)=? OR r.order_id=? OR LOWER(o.customer_email) LIKE ? ESCAPE '!' OR LOWER(o.customer_name) LIKE ? ESCAPE '!')");
            String like = "%" + t.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            args.add(t.toUpperCase(Locale.ROOT)); args.add(on == null ? -1L : on); args.add(like); args.add(like);
        }
        int page = Math.max(0, intOr(q.get("page"), 0)), size = Math.min(100, Math.max(1, intOr(q.get("size"), 25)));
        String from = " FROM order_return r JOIN orders o ON o.id=r.order_id";
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(size); pageArgs.add(page * size);
        boolean pii = actor.hasPermission("return:view-customer");
        List<Map<String, Object>> rows = support.rows("SELECT r.id, r.rma_number, r.order_id, r.status, r.return_kind, r.resolution, r.reason_code, r.refund_amount, r.store_id, r.risk_flags, r.auto_approved, r.evidence_required, r.assigned_operator_id, r.created_at, r.status_changed_at, o.vendor_id, o.customer_name, o.customer_email, "
                + "(SELECT COALESCE(SUM(i.quantity),0) FROM order_return_items i WHERE i.return_id=r.id) AS item_quantity" + from + where + " ORDER BY r.created_at DESC, r.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        for (Map<String, Object> row : rows) {
            Object flags = row.get("riskFlags");
            row.put("riskFlags", flags == null || flags.toString().isBlank() ? List.of() : Arrays.asList(flags.toString().split(",")));
            row.put("orderNumber", orderNumber(((Number) row.get("orderId")).longValue()));
            if (!pii) { row.put("customerName", mask((String) row.get("customerName"))); row.put("customerEmail", maskEmail((String) row.get("customerEmail"))); }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", rows); out.put("total", total); out.put("page", page); out.put("size", size);
        return out;
    }

    private static void eq(StringBuilder where, List<Object> args, String col, String v) { if (v != null && !v.isBlank()) { where.append(" AND ").append(col).append("=?"); args.add(v); } }

    private static void range(StringBuilder where, List<Object> args, String from, String to) {
        try {
            if (from != null && !from.isBlank()) { where.append(" AND r.created_at>=?"); args.add(Timestamp.from(java.time.LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant())); }
            if (to != null && !to.isBlank()) { where.append(" AND r.created_at<?"); args.add(Timestamp.from(java.time.LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())); }
        } catch (java.time.format.DateTimeParseException e) { throw bad("Dates must be yyyy-MM-dd."); }
    }

    private static int intOr(String v, int d) { try { return v == null ? d : Integer.parseInt(v); } catch (NumberFormatException e) { throw bad("Invalid number: " + v); } }

    static String mask(String s) { return s == null || s.isBlank() ? s : s.charAt(0) + "***"; }
    static String maskEmail(String e) { if (e == null || !e.contains("@")) return mask(e); int at = e.indexOf('@'); return e.charAt(0) + "***" + e.substring(at); }

    public Map<String, Object> details(AdminPrincipal actor, long id) {
        Map<String, Object> r = load(actor, id);
        UUID t = actor.tenantId();
        Map<String, Object> m = shape(r, true, null);
        Order order = orders.findById(((Number) r.get("orderId")).longValue()).orElseThrow(() -> notFound("Order"));
        boolean pii = actor.hasPermission("return:view-customer");
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", order.getId()); o.put("orderNumber", orderNumber(order.getId())); o.put("status", order.getStatus()); o.put("orderType", order.getOrderType()); o.put("storeId", order.getStoreId()); o.put("vendorId", order.getVendorId());
        o.put("total", order.getTotalAmount()); o.put("discount", order.getDiscountAmount()); o.put("tax", order.getTaxAmount()); o.put("deliveryCharge", order.getDeliveryCharge());
        o.put("paymentMethod", order.getPaymentMethod()); o.put("paymentStatus", order.getPaymentStatus()); o.put("refundStatus", order.getRefundStatus()); o.put("placedAt", order.getCreatedAt()); o.put("deliveredAt", order.getDeliveredAt());
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", order.getUserId());
        customer.put("name", pii ? order.getCustomerName() : mask(order.getCustomerName())); customer.put("email", pii ? order.getCustomerEmail() : maskEmail(order.getCustomerEmail()));
        if (pii) { customer.put("phone", order.getCustomerPhone()); customer.put("address", order.getDeliveryAddress()); }
        o.put("customer", customer);
        m.put("order", o);
        m.put("items", support.rows("SELECT * FROM order_return_items WHERE return_id=? ORDER BY id", id));
        m.put("history", support.rows("SELECT id, from_status, to_status, action, actor_id, actor_email, source, reason, created_at FROM return_status_history WHERE tenant_id=? AND return_id=? ORDER BY id", t, id));
        m.put("notes", support.rows("SELECT id, body, author_id, author_email, created_at FROM return_internal_notes WHERE tenant_id=? AND return_id=? ORDER BY id", t, id));
        m.put("attachments", attachments.list(t, id));
        m.put("inspections", support.rows("SELECT * FROM return_inspections WHERE tenant_id=? AND return_id=? ORDER BY id DESC", t, id));
        m.put("refunds", support.rows("SELECT * FROM return_refunds WHERE tenant_id=? AND return_id=? ORDER BY id", t, id));
        m.put("refundTransactions", support.rows("SELECT x.* FROM return_refund_attempts x JOIN return_refunds f ON f.id=x.refund_request_id WHERE f.tenant_id=? AND f.return_id=? ORDER BY x.id", t, id));
        m.put("exchanges", support.rows("SELECT * FROM exchange_requests WHERE tenant_id=? AND return_id=? ORDER BY id", t, id));
        m.put("pickups", support.rows("SELECT * FROM reverse_pickup_requests WHERE tenant_id=? AND return_id=? ORDER BY id", t, id));
        m.put("exceptions", support.rows("SELECT id, category, priority, status, summary, risk_factors, created_at FROM return_exceptions WHERE tenant_id=? AND return_id=? ORDER BY id", t, id));
        if (!pii) m.put("customerDetailsMasked", true);
        return m;
    }

    // ---- review actions --------------------------------------------------------------------------------------------------

    /** Approve all items or only some quantities. Evidence-required cases cannot be approved until evidence exists. */
    @Transactional
    public Map<String, Object> approve(AdminPrincipal admin, long id, Map<Long, Integer> quantities, String note) {
        Map<String, Object> r = lock(admin, id);
        String status = (String) r.get("status");
        if (!Set.of("REQUESTED", "UNDER_REVIEW").contains(status)) throw conflict("Only a new or under-review request can be approved (it is " + status + ").");
        if (Boolean.TRUE.equals(r.get("evidenceRequired")) && !attachments.hasCustomerEvidence(admin.tenantId(), id))
            throw conflict("The policy requires customer evidence for this reason. Request more information before approving.");
        List<Map<String, Object>> items = support.rows("SELECT * FROM order_return_items WHERE return_id=? ORDER BY id", id);
        boolean partial = false;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            long itemId = ((Number) it.get("id")).longValue();
            int requested = ((Number) it.get("quantity")).intValue();
            int approved = quantities == null || !quantities.containsKey(((Number) it.get("orderItemId")).longValue()) ? requested : quantities.get(((Number) it.get("orderItemId")).longValue());
            if (approved < 0 || approved > requested) throw bad("Approved quantity for item " + it.get("orderItemId") + " must be between 0 and the requested " + requested + ".");
            if (approved < requested) partial = true;
            jdbc.update("UPDATE order_return_items SET approved_quantity=? WHERE id=?", approved, itemId);
            total = total.add(((BigDecimal) it.get("unitPrice")).multiply(BigDecimal.valueOf(approved)));
        }
        if (total.signum() == 0) throw bad("Approve at least one unit, or reject the request.");
        jdbc.update("UPDATE order_return SET refund_amount=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP, approved_by=?, approved_at=CURRENT_TIMESTAMP, info_requested_note=NULL WHERE id=?", total.setScale(2, RoundingMode.HALF_UP), admin.userId(), admin.userId(), id);
        audit(admin.tenantId(), id, "RETURN", String.valueOf(id), partial ? "PARTIAL_APPROVE" : "APPROVE", Actor.of(admin), note);
        return move(admin.tenantId(), id, "APPROVED", Actor.of(admin), note == null || note.isBlank() ? (partial ? "Partially approved." : "Approved.") : note, partial ? "PARTIAL_APPROVE" : "APPROVE");
    }

    @Transactional
    public Map<String, Object> reject(AdminPrincipal admin, long id, String reason) {
        if (reason == null || reason.isBlank()) throw bad("A rejection reason is required.");
        Map<String, Object> r = lock(admin, id);
        jdbc.update("UPDATE order_return SET reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP, resolution_notes=? WHERE id=?", admin.userId(), clip(reason, 500), id);
        return move(admin.tenantId(), id, "REJECTED", Actor.of(admin), reason, "REJECT");
    }

    @Transactional
    public Map<String, Object> requestInfo(AdminPrincipal admin, long id, String note) {
        if (note == null || note.isBlank()) throw bad("Say what information is needed.");
        Map<String, Object> r = lock(admin, id);
        String status = (String) r.get("status");
        if (!Set.of("REQUESTED", "UNDER_REVIEW").contains(status)) throw conflict("Information can only be requested while the request is being reviewed.");
        jdbc.update("UPDATE order_return SET info_requested_note=? WHERE id=?", clip(note, 500), id);
        audit(admin.tenantId(), id, "RETURN", String.valueOf(id), "REQUEST_INFO", Actor.of(admin), note);
        if (status.equals("REQUESTED")) return move(admin.tenantId(), id, "UNDER_REVIEW", Actor.of(admin), note, "REQUEST_INFO");
        history(admin.tenantId(), id, status, status, "REQUEST_INFO", Actor.of(admin), note);
        publish("RETURN_INFO_REQUESTED", admin.tenantId(), id, note);
        return summary(admin.tenantId(), id);
    }

    @Transactional
    public Map<String, Object> addNote(AdminPrincipal admin, long id, String body) {
        if (body == null || body.isBlank()) throw bad("A note cannot be empty.");
        if (body.length() > 2000) throw bad("A note can have at most 2000 characters.");
        load(admin, id);
        jdbc.update("INSERT INTO return_internal_notes(tenant_id, return_id, body, author_id, author_email) VALUES (?,?,?,?,?)", admin.tenantId(), id, body.strip(), admin.userId(), admin.email());
        audit(admin.tenantId(), id, "NOTE", String.valueOf(id), "ADD_NOTE", Actor.of(admin), null);
        return Map.of("added", true);
    }

    @Transactional
    public Map<String, Object> assign(AdminPrincipal admin, long id, Long operatorId) {
        lock(admin, id);
        jdbc.update("UPDATE order_return SET assigned_operator_id=? WHERE id=? AND tenant_id=?", operatorId, id, admin.tenantId());
        audit(admin.tenantId(), id, "RETURN", String.valueOf(id), "ASSIGN", Actor.of(admin), String.valueOf(operatorId));
        return summary(admin.tenantId(), id);
    }

    /** Manual status changes allowed from the console. Approval, rejection, refund, exchange and pickup progress have their own checked actions. */
    private static final Set<String> MANUAL_TARGETS = Set.of("UNDER_REVIEW", "CUSTOMER_CANCELLED", "RECEIVED", "INSPECTION_PENDING", "REFUND_PENDING", "CLOSED");

    @Transactional
    public Map<String, Object> transition(AdminPrincipal admin, long id, String to, String reason) {
        if (to == null) throw bad("A target status is required.");
        Map<String, Object> r = lock(admin, id);
        String kind = (String) r.get("returnKind"), from = (String) r.get("status");
        if (!ReturnStateMachine.known(kind).contains(to)) throw bad("Unknown status " + to + ".");
        if (!MANUAL_TARGETS.contains(to)) {
            String owner = ReturnStateMachine.PAYMENT_OWNED.contains(to) ? "the Payment service confirmation" : ReturnStateMachine.DELIVERY_OWNED.contains(to) ? "the Delivery service" : "its dedicated action";
            throw conflict(to + " can only be set through " + owner + ", not by hand.");
        }
        if (!ReturnStateMachine.canMove(kind, from, to)) throw conflict("A return that is " + from + " cannot move to " + to + ". Allowed: " + ReturnStateMachine.allowed(kind, from) + ".");
        if (ReturnStateMachine.REASON_REQUIRED.contains(to) && (reason == null || reason.isBlank())) throw bad("A reason is required to move to " + to + ".");
        Policy policy = policies.version(admin.tenantId(), r.get("policyVersionId") == null ? null : ((Number) r.get("policyVersionId")).longValue(), kind);
        if (from.equals("RECEIVED") && to.equals("REFUND_PENDING") && policy.inspectionRequired()) throw conflict("The policy requires inspection before a refund.");
        if (from.equals("RECEIVED") && to.equals("INSPECTION_PENDING") && !policy.inspectionRequired()) throw conflict("The policy does not require inspection.");
        if (to.equals("REFUND_PENDING")) requireNoOpenRefund(admin.tenantId(), id);
        if (to.equals("RECEIVED")) {
            jdbc.update("UPDATE order_return SET received_at=CURRENT_TIMESTAMP WHERE id=?", id);
            jdbc.update("UPDATE order_return_items SET received_quantity=COALESCE(approved_quantity, quantity) WHERE return_id=? AND received_quantity IS NULL", id);
        }
        return move(admin.tenantId(), id, to, Actor.of(admin), reason, "TRANSITION");
    }

    private void requireNoOpenRefund(UUID tenant, long returnId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM return_refunds WHERE tenant_id=? AND return_id=? AND kind='RETURN' AND status IN ('CALCULATED','APPROVED','PROCESSING','COMPLETED')", Integer.class, tenant, returnId);
        if (n != null && n > 0) throw conflict("A refund already exists for this return.");
    }

    @Transactional
    public Map<String, Object> customerCancel(UUID tenant, long customerId, long id, String reason) {
        Map<String, Object> r = support.one("SELECT * FROM order_return WHERE id=? AND tenant_id=? AND user_id=?", "Return", id, tenant, customerId);
        jdbc.query("SELECT id FROM order_return WHERE id=? FOR UPDATE", rs -> {}, id);
        String from = (String) support.one("SELECT status FROM order_return WHERE id=?", "Return", id).get("status");
        if (!Set.of("REQUESTED", "UNDER_REVIEW", "APPROVED").contains(from)) throw conflict("A return that is " + from + " can no longer be cancelled.");
        return move(tenant, id, "CUSTOMER_CANCELLED", Actor.customer(customerId), reason == null || reason.isBlank() ? "Cancelled by customer." : reason, "CUSTOMER_CANCEL");
    }

    /** Locks the row and re-reads it, after the tenant and store check. */
    Map<String, Object> lock(AdminPrincipal admin, long id) {
        load(admin, id);
        jdbc.query("SELECT id FROM order_return WHERE id=? AND tenant_id=? FOR UPDATE", rs -> {}, id, admin.tenantId());
        return load(admin, id);
    }

    // ---- the one place a status changes ----------------------------------------------------------------------------------

    /**
     * Compare-and-set status change. Callers have already decided the change is allowed; this validates the lifecycle edge once more,
     * writes the history and audit rows, and publishes the notification event.
     */
    @Transactional
    public Map<String, Object> move(UUID tenant, long id, String to, Actor actor, String reason, String action) {
        Map<String, Object> r = support.one("SELECT status, return_kind FROM order_return WHERE id=? AND tenant_id=?", "Return", id, tenant);
        String from = (String) r.get("status"), kind = (String) r.get("returnKind");
        if (from.equals(to)) return summary(tenant, id);
        if (!ReturnStateMachine.canMove(kind, from, to)) throw conflict("A return that is " + from + " cannot move to " + to + ".");
        String extra = ReturnStateMachine.isTerminal(to) || to.equals("CLOSED") ? ", closed_at=CURRENT_TIMESTAMP" : "";
        int changed = jdbc.update("UPDATE order_return SET status=?, status_changed_at=CURRENT_TIMESTAMP, rma_version=rma_version+1" + extra + (to.equals("REFUNDED") || to.equals("REJECTED") ? ", resolved_at=CURRENT_TIMESTAMP" : "")
                + " WHERE id=? AND tenant_id=? AND status=?", to, id, tenant, from);
        if (changed == 0) throw conflict("The return was changed by someone else. Reload and try again.");
        history(tenant, id, from, to, action, actor, reason);
        audit(tenant, id, "RETURN", String.valueOf(id), action, actor, from + " -> " + to + (reason == null ? "" : ": " + reason));
        publish("RETURN_" + to, tenant, id, reason);
        return summary(tenant, id);
    }

    void history(UUID tenant, long id, String from, String to, String action, Actor actor, String reason) {
        jdbc.update("INSERT INTO return_status_history(tenant_id, return_id, from_status, to_status, action, actor_id, actor_email, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                tenant, id, from, to, action, actor.id(), actor.email(), actor.source(), clip(reason, 500), UUID.randomUUID().toString());
    }

    public void audit(UUID tenant, Long returnId, String entityType, String entityId, String action, Actor actor, String detail) {
        jdbc.update("INSERT INTO return_audit_logs(tenant_id, return_id, entity_type, entity_id, action, actor_id, actor_email, detail) VALUES (?,?,?,?,?,?,?,?)",
                tenant, returnId, entityType, entityId, action, actor.id(), actor.email(), clip(detail, 4000));
    }

    /** Notification event; a broker outage must never roll back or fail the business change. */
    public void publish(String type, UUID tenant, long id, String reason) {
        try {
            Map<String, Object> r = jdbc.queryForMap("SELECT order_id, user_id, status, refund_amount FROM order_return WHERE id=? AND tenant_id=?", id, tenant);
            producer.publishOrderEvent(new OrderEvent(type, ((Number) r.get("order_id")).longValue(), ((Number) r.get("user_id")).longValue(), (String) r.get("status"), (BigDecimal) r.get("refund_amount"), clip(reason, 255), "RMA-" + id + "-" + r.get("status")));
        } catch (RuntimeException e) { log.warn("Could not publish {} for return {}: {}", type, id, e.getMessage()); }
    }

    static String clip(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max); }
}

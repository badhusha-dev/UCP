package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.IdempotencyService;
import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Refund requests for returns, claims and exchange price differences. This service calculates and tracks; the Payment service moves
 * the money. A refund is never COMPLETED (and a return never REFUNDED) until Payment confirms through the authenticated callback, and
 * this module never writes to payment or accounting tables. Every attempt carries an idempotency key that Payment must echo back, so
 * retries and duplicate confirmations cannot refund twice.
 */
@Slf4j
@Service
public class RefundService {
    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final OrderRepository orders;
    private final ReturnService returns;
    private final PolicyService policies;
    private final ReturnSettingsService settings;
    private final ReturnExceptionService exceptions;
    private final IdempotencyService idempotency;
    private final OrderProducerService producer;
    private final ObjectMapper mapper;

    public RefundService(JdbcTemplate jdbc, AdminSupport support, OrderRepository orders, ReturnService returns, PolicyService policies, ReturnSettingsService settings,
                         ReturnExceptionService exceptions, IdempotencyService idempotency, OrderProducerService producer, ObjectMapper mapper) {
        this.jdbc = jdbc; this.support = support; this.orders = orders; this.returns = returns; this.policies = policies; this.settings = settings; this.exceptions = exceptions;
        this.idempotency = idempotency; this.producer = producer; this.mapper = mapper;
    }

    // ---- calculation -----------------------------------------------------------------------------------------------------

    /**
     * Creates the refund request for a return: the amount is computed here from what was accepted (or approved, when the policy waives
     * inspection), never typed in. The return moves to REFUND_PENDING if it is not there yet.
     */
    @Transactional
    public Map<String, Object> prepare(AdminPrincipal admin, long returnId, String method) {
        Map<String, Object> r = returns.lock(admin, returnId);
        String kind = (String) r.get("returnKind"), status = (String) r.get("status");
        String m = method == null ? ("STORE_CREDIT".equals(r.get("resolution")) ? "STORE_CREDIT" : "ORIGINAL") : method;
        if (!Set.of("ORIGINAL", "STORE_CREDIT").contains(m)) throw bad("method must be ORIGINAL or STORE_CREDIT.");
        Policy policy = policies.version(admin.tenantId(), r.get("policyVersionId") == null ? null : ((Number) r.get("policyVersionId")).longValue(), kind);
        if (m.equals("STORE_CREDIT") && !policy.storeCreditAllowed()) throw bad("Store credit is not allowed by the return policy.");
        if ("EXCHANGE".equals(r.get("resolution"))) throw conflict("This return is an exchange. Refunds for it are limited to a price difference.");
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM return_refunds WHERE tenant_id=? AND return_id=? AND kind IN ('RETURN','CLAIM') AND status IN ('CALCULATED','APPROVED','PROCESSING','COMPLETED')", Integer.class, admin.tenantId(), returnId);
        if (existing != null && existing > 0) throw conflict("A refund already exists for this return.");
        boolean claim = ReturnStateMachine.CLAIM.equals(kind);
        switch (status) {
            case "REFUND_PENDING" -> { }
            case "INSPECTION_COMPLETED" -> requireAcceptance(admin.tenantId(), returnId);
            case "RECEIVED" -> { if (policy.inspectionRequired()) throw conflict("The policy requires inspection before a refund."); }
            case "APPROVED" -> { if (!claim) throw conflict("The goods must be received (and inspected, when required) before a refund."); }
            default -> throw conflict("A refund cannot be prepared while the return is " + status + ".");
        }
        Order order = orders.findByIdForUpdate(((Number) r.get("orderId")).longValue()).filter(o -> o.getTenantId().equals(admin.tenantId())).orElseThrow(() -> notFound("Order"));
        List<Map<String, Object>> items = support.rows("SELECT * FROM order_return_items WHERE return_id=? ORDER BY id", returnId);
        List<RefundCalculator.Line> lines = new ArrayList<>();
        boolean inspected = status.equals("INSPECTION_COMPLETED") || hasInspection(admin.tenantId(), returnId) ;
        for (Map<String, Object> it : items) {
            int qty = inspected && it.get("acceptedQuantity") != null ? ((Number) it.get("acceptedQuantity")).intValue()
                    : it.get("approvedQuantity") != null ? ((Number) it.get("approvedQuantity")).intValue() : ((Number) it.get("quantity")).intValue();
            if (qty > 0) lines.add(new RefundCalculator.Line(((Number) it.get("orderItemId")).longValue(), (BigDecimal) it.get("unitPrice"), qty));
        }
        if (lines.isEmpty()) throw conflict("No units are accepted for refund.");
        boolean fullReturn = coversEveryUnit(order, returnId, lines);
        BigDecimal subtotal = order.getItems().stream().map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserved = reservedForOrder(admin.tenantId(), order.getId(), 0L);
        boolean deliveryDone = Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM return_refunds WHERE tenant_id=? AND order_id=? AND status IN ('APPROVED','PROCESSING','COMPLETED') AND delivery_fee_refund>0", Boolean.class, admin.tenantId(), order.getId()));
        boolean sellerFault = policy.reason((String) r.get("reasonCode")).map(Policy.Reason::sellerFault).orElse(false);
        RefundCalculator.Result calc = RefundCalculator.calculate(new RefundCalculator.Order(subtotal, nz(order.getDiscountAmount()), nz(order.getTaxAmount()), nz(order.getDeliveryCharge()), order.getTotalAmount(), reserved, deliveryDone),
                lines, fullReturn, sellerFault || claim, policy);
        if (calc.finalAmount().signum() <= 0) throw conflict("Nothing is left to refund for this order (" + calc.refundableRemaining() + " remaining).");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO return_refunds(tenant_id, return_id, order_id, kind, method, status, original_amount, previously_refunded, items_amount, discount_allocation, tax_adjustment, delivery_fee_refund, fees, final_amount, calculation, requested_by) VALUES (?,?,?,?,?,'CALCULATED',?,?,?,?,?,?,?,?,?,?)", new String[]{"id"});
            int n = 1;
            ps.setObject(n++, admin.tenantId()); ps.setLong(n++, returnId); ps.setLong(n++, order.getId()); ps.setString(n++, claim ? "CLAIM" : "RETURN"); ps.setString(n++, m);
            ps.setBigDecimal(n++, order.getTotalAmount()); ps.setBigDecimal(n++, reserved); ps.setBigDecimal(n++, calc.itemsAmount()); ps.setBigDecimal(n++, calc.discountAllocation()); ps.setBigDecimal(n++, calc.taxAdjustment());
            ps.setBigDecimal(n++, calc.deliveryFeeRefund()); ps.setBigDecimal(n++, calc.fees()); ps.setBigDecimal(n++, calc.finalAmount()); ps.setString(n++, String.join(" ", calc.notes())); ps.setLong(n, admin.userId());
            return ps;
        }, kh);
        long refundId = ((Number) kh.getKeys().get("id")).longValue();
        jdbc.update("UPDATE order_return SET refund_amount=? WHERE id=?", calc.finalAmount(), returnId);
        returns.audit(admin.tenantId(), returnId, "REFUND", String.valueOf(refundId), "CALCULATE", ReturnService.Actor.of(admin), calc.finalAmount() + " via " + m);
        if (!status.equals("REFUND_PENDING")) returns.move(admin.tenantId(), returnId, "REFUND_PENDING", ReturnService.Actor.of(admin), "Refund calculated.", "REFUND_PREPARE");
        return view(admin.tenantId(), refundId);
    }

    private boolean hasInspection(UUID tenant, long returnId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM return_inspections WHERE tenant_id=? AND return_id=?", Integer.class, tenant, returnId);
        return n != null && n > 0;
    }

    private void requireAcceptance(UUID tenant, long returnId) {
        List<String> o = jdbc.queryForList("SELECT outcome FROM return_inspections WHERE tenant_id=? AND return_id=? ORDER BY id DESC", String.class, tenant, returnId);
        if (o.isEmpty() || !Set.of("ACCEPTED_FOR_REFUND", "PARTIALLY_ACCEPTED").contains(o.get(0))) throw conflict("The inspection did not accept goods for a refund.");
    }

    private boolean coversEveryUnit(Order order, long returnId, List<RefundCalculator.Line> lines) {
        Map<Long, Integer> returned = new HashMap<>();
        jdbc.query("SELECT i.order_item_id, SUM(COALESCE(i.refunded_quantity,0)) FROM order_return_items i JOIN order_return r ON r.id=i.return_id WHERE r.order_id=? AND r.id<>? GROUP BY i.order_item_id", rs -> { returned.put(rs.getLong(1), rs.getInt(2)); }, order.getId(), returnId);
        lines.forEach(l -> returned.merge(l.orderItemId(), l.quantity(), Integer::sum));
        return order.getItems().stream().allMatch(i -> returned.getOrDefault(i.getId(), 0) >= i.getQuantity());
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** Money already refunded or committed to refund on this order by other requests; the cap for the next one. */
    BigDecimal reservedForOrder(UUID tenant, long orderId, long excludingRefundId) {
        BigDecimal v = jdbc.queryForObject("SELECT COALESCE(SUM(final_amount),0) FROM return_refunds WHERE tenant_id=? AND order_id=? AND id<>? AND status IN ('APPROVED','PROCESSING','COMPLETED')", BigDecimal.class, tenant, orderId, excludingRefundId);
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---- approval and processing -----------------------------------------------------------------------------------------

    /** Small refunds need no second person; refunds above the tenant threshold cannot be approved by whoever calculated them. */
    @Transactional
    public Map<String, Object> approve(AdminPrincipal admin, long refundId) {
        Map<String, Object> f = lockRefund(admin, refundId);
        if (!"CALCULATED".equals(f.get("status"))) throw conflict("Only a calculated refund can be approved (it is " + f.get("status") + ").");
        BigDecimal amount = (BigDecimal) f.get("finalAmount");
        if (amount.compareTo(settings.get(admin.tenantId()).refundApprovalThreshold()) > 0) {
            if (!admin.hasPermission("return:refund-approve")) throw forbidden("Refunds above the approval threshold need the return:refund-approve permission.");
            if (f.get("requestedBy") != null && ((Number) f.get("requestedBy")).longValue() == admin.userId()) throw forbidden("A refund above the approval threshold must be approved by someone other than the person who calculated it.");
        }
        BigDecimal reserved = reservedForOrder(admin.tenantId(), ((Number) f.get("orderId")).longValue(), refundId);
        Order order = orders.findByIdForUpdate(((Number) f.get("orderId")).longValue()).orElseThrow(() -> notFound("Order"));
        if (reserved.add(amount).compareTo(order.getTotalAmount()) > 0) throw conflict("Approving this would refund more than the customer paid (already committed " + reserved + " of " + order.getTotalAmount() + ").");
        jdbc.update("UPDATE return_refunds SET status='APPROVED', approved_by=?, approved_at=CURRENT_TIMESTAMP, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", admin.userId(), refundId);
        returns.audit(admin.tenantId(), ((Number) f.get("returnId")).longValue(), "REFUND", String.valueOf(refundId), "APPROVE", ReturnService.Actor.of(admin), amount.toPlainString());
        if (amount.compareTo(settings.get(admin.tenantId()).highValueRefundThreshold()) >= 0)
            exceptions.raise(admin.tenantId(), ((Number) f.get("returnId")).longValue(), "THRESHOLD_EXCEEDED", "MEDIUM", "Refund of " + amount + " is at or above the high-value threshold.", null, "refund-" + refundId);
        return view(admin.tenantId(), refundId);
    }

    /** Sends the refund to Payment. The attempt is recorded first, in the same transaction, and the money is only confirmed by the callback. */
    @Transactional
    public Map<String, Object> process(AdminPrincipal admin, long refundId) {
        Map<String, Object> f = lockRefund(admin, refundId);
        String status = (String) f.get("status");
        if (!Set.of("APPROVED", "FAILED").contains(status)) throw conflict("A refund must be approved before it is processed (it is " + status + ").");
        long returnId = ((Number) f.get("returnId")).longValue(), orderId = ((Number) f.get("orderId")).longValue();
        BigDecimal amount = (BigDecimal) f.get("finalAmount");
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> notFound("Order"));
        if (reservedForOrder(admin.tenantId(), orderId, refundId).add(amount).compareTo(order.getTotalAmount()) > 0)
            throw conflict("This refund would take total refunds above what the customer paid.");
        Integer inFlight = jdbc.queryForObject("SELECT COUNT(*) FROM return_refund_attempts WHERE tenant_id=? AND refund_request_id=? AND status='PENDING'", Integer.class, admin.tenantId(), refundId);
        if (inFlight != null && inFlight > 0) throw conflict("A refund attempt is already waiting for the Payment service.");
        int attempt = ((Number) f.get("attemptCount")).intValue() + 1;
        String key = "rf-" + refundId + "-" + attempt;
        jdbc.update("INSERT INTO return_refund_attempts(tenant_id, refund_request_id, attempt_no, idempotency_key, status, amount, requested_by) VALUES (?,?,?,?,'PENDING',?,?)", admin.tenantId(), refundId, attempt, key, amount, admin.userId());
        jdbc.update("UPDATE return_refunds SET status='PROCESSING', attempt_count=?, failure_reason=NULL, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", attempt, refundId);
        boolean ownedByReturn = !"PRICE_DIFFERENCE".equals(f.get("kind"));
        String returnStatus = (String) returns.load(admin, returnId).get("status");
        if (ownedByReturn && Set.of("REFUND_PENDING", "REFUND_FAILED").contains(returnStatus))
            returns.move(admin.tenantId(), returnId, "REFUND_PROCESSING", ReturnService.Actor.of(admin), "Refund sent to Payment (attempt " + attempt + ").", "REFUND_PROCESS");
        jdbc.update("UPDATE orders SET refund_status='PENDING' WHERE id=? AND refund_status<>'COMPLETED'", orderId);
        returns.audit(admin.tenantId(), returnId, "REFUND", String.valueOf(refundId), "PROCESS", ReturnService.Actor.of(admin), "attempt " + attempt + " key " + key);
        try {
            producer.publishOrderEvent(new OrderEvent("RETURN_REFUND_REQUESTED", orderId, order.getUserId(), "REFUND_PROCESSING", amount, f.get("method") + ":" + refundId, key));
        } catch (RuntimeException e) { log.warn("Refund request event for {} could not be published: {}", key, e.getMessage()); }
        return view(admin.tenantId(), refundId);
    }

    // ---- Payment callback ------------------------------------------------------------------------------------------------

    /**
     * The only place a refund becomes COMPLETED or FAILED. Authenticated by the service token at the controller; here the attempt key,
     * the tenant and the amount are checked, and the event id makes a redelivery a no-op.
     */
    public Map<String, Object> outcome(UUID tenant, String eventId, String attemptKey, boolean succeeded, String paymentReference, BigDecimal amount, String failureReason) {
        if (eventId == null || !eventId.matches("[A-Za-z0-9_.:-]{6,100}")) throw bad("eventId is required.");
        Map<String, Object> tx = support.one("SELECT x.*, f.order_id, f.return_id, f.kind, f.final_amount FROM return_refund_attempts x JOIN return_refunds f ON f.id=x.refund_request_id WHERE x.tenant_id=? AND x.idempotency_key=?", "Refund attempt", tenant, attemptKey);
        long orderId = ((Number) tx.get("orderId")).longValue();
        if (succeeded && "PENDING".equals(tx.get("status")) && (amount == null || amount.setScale(2, RoundingMode.HALF_UP).compareTo((BigDecimal) tx.get("amount")) != 0)) {
            // recorded outside the callback's transaction so the flag survives the refusal
            long returnId = ((Number) tx.get("returnId")).longValue();
            exceptions.raise(tenant, returnId, "REFUND_AMOUNT_MISMATCH", "CRITICAL", "Payment confirmed " + amount + " but " + tx.get("amount") + " was requested.", "attempt " + attemptKey, "tx-" + tx.get("id"));
            returns.audit(tenant, returnId, "REFUND", String.valueOf(tx.get("refundRequestId")), "AMOUNT_MISMATCH", ReturnService.Actor.system("PAYMENT"), "confirmed " + amount + " expected " + tx.get("amount"));
            throw conflict("Confirmed amount does not match the amount requested. The refund stays PROCESSING and was flagged for review.");
        }
        return idempotency.once(tenant, "PAYMENT-REFUND", eventId, orderId, () -> apply(tenant, tx, succeeded, paymentReference, amount, failureReason));
    }

    private Map<String, Object> apply(UUID tenant, Map<String, Object> tx, boolean succeeded, String paymentReference, BigDecimal amount, String failureReason) {
        long txId = ((Number) tx.get("id")).longValue(), refundId = ((Number) tx.get("refundRequestId")).longValue(), returnId = ((Number) tx.get("returnId")).longValue(), orderId = ((Number) tx.get("orderId")).longValue();
        jdbc.query("SELECT id FROM return_refunds WHERE id=? AND tenant_id=? FOR UPDATE", rs -> {}, refundId, tenant);
        String txStatus = jdbc.queryForObject("SELECT status FROM return_refund_attempts WHERE id=?", String.class, txId);
        if (!"PENDING".equals(txStatus)) return Map.of("refundRequestId", refundId, "status", txStatus, "ignored", "Attempt already " + txStatus + ".");
        BigDecimal expected = (BigDecimal) tx.get("amount");
        ReturnService.Actor payment = ReturnService.Actor.system("PAYMENT");
        if (succeeded) {
            if (amount == null || amount.setScale(2, RoundingMode.HALF_UP).compareTo(expected) != 0) {
                exceptions.raise(tenant, returnId, "REFUND_AMOUNT_MISMATCH", "CRITICAL", "Payment confirmed " + amount + " but " + expected + " was requested.", "attempt " + tx.get("idempotencyKey"), "tx-" + txId);
                returns.audit(tenant, returnId, "REFUND", String.valueOf(refundId), "AMOUNT_MISMATCH", payment, "confirmed " + amount + " expected " + expected);
                throw conflict("Confirmed amount does not match the amount requested. The refund stays PROCESSING and was flagged for review.");
            }
            jdbc.update("UPDATE return_refund_attempts SET status='SUCCEEDED', payment_reference=?, confirmed_at=CURRENT_TIMESTAMP WHERE id=?", ReturnService.clip(paymentReference, 100), txId);
            jdbc.update("UPDATE return_refunds SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP, failure_reason=NULL, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", refundId);
            BigDecimal done = jdbc.queryForObject("SELECT COALESCE(SUM(final_amount),0) FROM return_refunds WHERE tenant_id=? AND order_id=? AND status='COMPLETED'", BigDecimal.class, tenant, orderId);
            BigDecimal total = jdbc.queryForObject("SELECT total_amount FROM orders WHERE id=?", BigDecimal.class, orderId);
            jdbc.update("UPDATE orders SET refund_status=?, refund_reference=?, payment_status=CASE WHEN ?>=total_amount THEN 'REFUNDED' ELSE payment_status END WHERE id=?",
                    done.compareTo(total) >= 0 ? "COMPLETED" : "PARTIAL", ReturnService.clip(paymentReference, 100), done, orderId);
            if (!"PRICE_DIFFERENCE".equals(tx.get("kind"))) {
                jdbc.update("UPDATE order_return_items SET refunded_quantity=COALESCE(accepted_quantity, approved_quantity, quantity) WHERE return_id=?", returnId);
                returns.move(tenant, returnId, "REFUNDED", payment, "Payment confirmed " + expected + " (" + paymentReference + ").", "REFUND_CONFIRMED");
            }
            returns.audit(tenant, returnId, "REFUND", String.valueOf(refundId), "COMPLETED", payment, expected + " ref " + paymentReference);
            returns.publish("RETURN_REFUND_COMPLETED", tenant, returnId, expected.toPlainString());
            return Map.of("refundRequestId", refundId, "status", "COMPLETED");
        }
        String reason = failureReason == null || failureReason.isBlank() ? "Payment declined the refund." : ReturnService.clip(failureReason, 255);
        jdbc.update("UPDATE return_refund_attempts SET status='FAILED', failure_reason=?, confirmed_at=CURRENT_TIMESTAMP WHERE id=?", reason, txId);
        jdbc.update("UPDATE return_refunds SET status='FAILED', failure_reason=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", reason, refundId);
        jdbc.update("UPDATE orders SET refund_status='FAILED' WHERE id=? AND refund_status<>'COMPLETED'", orderId);
        if (!"PRICE_DIFFERENCE".equals(tx.get("kind"))) returns.move(tenant, returnId, "REFUND_FAILED", payment, reason, "REFUND_FAILED");
        exceptions.raise(tenant, returnId, "REFUND_FAILURE", "HIGH", "Refund attempt " + tx.get("attemptNo") + " failed: " + reason, null, "tx-" + txId);
        returns.audit(tenant, returnId, "REFUND", String.valueOf(refundId), "FAILED", payment, reason);
        returns.publish("RETURN_REFUND_FAILED", tenant, returnId, reason);
        return Map.of("refundRequestId", refundId, "status", "FAILED");
    }

    // ---- reading ---------------------------------------------------------------------------------------------------------

    Map<String, Object> lockRefund(AdminPrincipal admin, long refundId) {
        Map<String, Object> f = support.one("SELECT * FROM return_refunds WHERE id=? AND tenant_id=?", "Refund", refundId, admin.tenantId());
        returns.load(admin, ((Number) f.get("returnId")).longValue());
        jdbc.query("SELECT id FROM return_refunds WHERE id=? AND tenant_id=? FOR UPDATE", rs -> {}, refundId, admin.tenantId());
        return support.one("SELECT * FROM return_refunds WHERE id=? AND tenant_id=?", "Refund", refundId, admin.tenantId());
    }

    public Map<String, Object> view(UUID tenant, long refundId) {
        Map<String, Object> f = support.one("SELECT * FROM return_refunds WHERE id=? AND tenant_id=?", "Refund", refundId, tenant);
        f.put("transactions", support.rows("SELECT id, attempt_no, idempotency_key, status, amount, payment_reference, failure_reason, requested_at, confirmed_at FROM return_refund_attempts WHERE tenant_id=? AND refund_request_id=? ORDER BY attempt_no", tenant, refundId));
        f.put("netOfFees", ((BigDecimal) f.get("finalAmount")));
        return f;
    }

    public Map<String, Object> viewFor(AdminPrincipal admin, long refundId) {
        Map<String, Object> f = support.one("SELECT return_id FROM return_refunds WHERE id=? AND tenant_id=?", "Refund", refundId, admin.tenantId());
        returns.load(admin, ((Number) f.get("returnId")).longValue());
        return view(admin.tenantId(), refundId);
    }

    public Map<String, Object> list(AdminPrincipal admin, Map<String, String> q) {
        List<Object> args = new ArrayList<>(); args.add(admin.tenantId());
        StringBuilder w = new StringBuilder(" WHERE f.tenant_id=?");
        if (!admin.hasPermission("order:view-all-stores")) {
            if (admin.storeIds().isEmpty()) w.append(" AND 1=0");
            else { w.append(" AND r.store_id IN (").append(admin.storeIds().stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","))).append(")"); args.addAll(admin.storeIds()); }
        }
        if (q.get("status") != null && !q.get("status").isBlank()) { w.append(" AND f.status=?"); args.add(q.get("status")); }
        int page = Math.max(0, Integer.parseInt(q.getOrDefault("page", "0"))), size = Math.min(100, Math.max(1, Integer.parseInt(q.getOrDefault("size", "25"))));
        String from = " FROM return_refunds f JOIN order_return r ON r.id=f.return_id";
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + from + w, Integer.class, args.toArray());
        args.add(size); args.add(page * size);
        return Map.of("items", support.rows("SELECT f.id, f.return_id, r.rma_number, f.order_id, f.kind, f.method, f.status, f.original_amount, f.previously_refunded, f.final_amount, f.attempt_count, f.failure_reason, f.created_at, f.completed_at"
                + from + w + " ORDER BY f.created_at DESC, f.id DESC LIMIT ? OFFSET ?", args.toArray()), "total", total, "page", page, "size", size);
    }
}

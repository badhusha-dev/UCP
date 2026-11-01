package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderReturn;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderReturnRepository;
import com.ecommerce.order.repository.OrderTrackingEventRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.ecommerce.order.service.PricingClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Supplier;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Administrative order actions. Every status change goes through {@link OrderStateMachine} plus ownership, payment and permission
 * rules, is recorded in order_status_history with actor, reason and correlation id, and sensitive operations are idempotent.
 * The order row is locked for the change and also carries an optimistic-lock version.
 */
@Slf4j
@Service
public class OrderAdminService {
    /** Statuses reached only by recording fulfillments (see FulfillmentService); an administrator cannot assert them directly. */
    static final Set<String> FULFILLMENT_DRIVEN = Set.of("PARTIALLY_FULFILLED", "FULFILLED");
    static final Set<String> PAYMENT_GATED = Set.of("PROCESSING", "PREPARING", "READY_FOR_FULFILLMENT", "READY_FOR_PICKUP", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED");

    private final OrderRepository orders;
    private final OrderTrackingEventRepository tracking;
    private final OrderReturnRepository returns;
    private final OrderSettingsService settings;
    private final OrderProducerService producer;
    private final PricingClient pricing;
    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final OrderQueryService query;
    private final IdempotencyService idempotency;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy private com.ecommerce.order.rma.ExchangeService exchanges;

    public OrderAdminService(OrderRepository orders, OrderTrackingEventRepository tracking, OrderReturnRepository returns, OrderSettingsService settings,
                             OrderProducerService producer, PricingClient pricing, JdbcTemplate jdbc, AdminSupport support, ObjectMapper mapper,
                             PlatformTransactionManager tm, OrderQueryService query, IdempotencyService idempotency) {
        this.idempotency = idempotency;
        this.orders = orders; this.tracking = tracking; this.returns = returns; this.settings = settings; this.producer = producer; this.pricing = pricing;
        this.jdbc = jdbc; this.support = support; this.mapper = mapper; this.tx = new TransactionTemplate(tm); this.query = query;
    }

    // ---- loading ---------------------------------------------------------------------------------------------------

    /** Loads an order that belongs to the caller's tenant and an authorised store; anything else is "not found". */
    public Order load(AdminPrincipal actor, long id, boolean lock) {
        Order o = (lock ? orders.findByIdForUpdate(id) : orders.findById(id)).orElseThrow(() -> notFound("Order"));
        if (!o.getTenantId().equals(actor.tenantId()) || !actor.canSeeStore(o.getStoreId())) throw notFound("Order");
        return o;
    }

    // ---- idempotency -----------------------------------------------------------------------------------------------

    /** Runs {@code work} at most once per (tenant, key). A retry with the same key and request returns the first result. */
    public Map<String, Object> idempotent(AdminPrincipal actor, String key, String action, Long orderId, String fingerprint, Supplier<Map<String, Object>> work) {
        return idempotency.run(actor, key, action, orderId, fingerprint, work);
    }

    // ---- state changes ---------------------------------------------------------------------------------------------

    public static String permissionFor(String target) {
        return switch (target) {
            case "CONFIRMED", "ACCEPTED", "REJECTED" -> "order:confirm";
            case "ON_HOLD" -> "order:hold";
            case "CANCELLED", "CANCELLATION_REQUESTED" -> "order:cancel";
            default -> "order:process";
        };
    }

    /** The transitions this caller could perform on this order right now, with the reason why others are excluded. */
    public List<Map<String, Object>> allowedTransitions(AdminPrincipal actor, Order o, OrderSettingsService.Settings cfg) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String to : OrderStateMachine.allowed(o.getOrderType(), o.getStatus())) {
            String blocked = blockedReason(actor, o, to, cfg, false);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("to", to); m.put("allowed", blocked == null); m.put("blockedReason", blocked);
            m.put("reasonRequired", OrderStateMachine.REASON_REQUIRED.contains(to)); m.put("permission", permissionFor(to));
            out.add(m);
        }
        return out;
    }

    /** Null when the transition may proceed; otherwise the user-facing reason. {@code system} bypasses permission and ownership rules only. */
    String blockedReason(AdminPrincipal actor, Order o, String to, OrderSettingsService.Settings cfg, boolean system) {
        if (!OrderStateMachine.canMove(o.getOrderType(), o.getStatus(), to)) return "Not allowed from " + o.getStatus() + ".";
        if (system) return null;
        if (OrderStateMachine.SYSTEM_ONLY_TARGETS.contains(to)) return "Set by the Payment/Accounting side, not by an administrator.";
        if (FULFILLMENT_DRIVEN.contains(to)) return "Reached by recording fulfillments on the Fulfillment tab.";
        if (OrderStateMachine.DELIVERY_OWNED_TARGETS.contains(to) && o.getDeliveryReference() != null) return "Owned by the Delivery service for this order.";
        if (!actor.hasPermission(permissionFor(to))) return "Requires permission " + permissionFor(to) + ".";
        if (cfg.requirePaymentBeforeProcessing() && PAYMENT_GATED.contains(to) && !"PAID".equals(o.getPaymentStatus())) return "Payment must be confirmed first (tenant policy).";
        return null;
    }

    private void assertTransition(AdminPrincipal actor, Order o, String to, String reason, OrderSettingsService.Settings cfg, boolean system) {
        if (!OrderStateMachine.isKnown(o.getOrderType(), to)) throw bad("Unknown status " + to + " for a " + o.getOrderType() + " order.");
        String blocked = blockedReason(actor, o, to, cfg, system);
        if (blocked != null) {
            boolean permission = blocked.startsWith("Requires permission");
            throw permission ? forbidden(blocked) : conflict("Cannot move order " + orderNumber(o.getId()) + " from " + o.getStatus() + " to " + to + ": " + blocked
                    + " Allowed next: " + OrderStateMachine.allowed(o.getOrderType(), o.getStatus()));
        }
        if (OrderStateMachine.REASON_REQUIRED.contains(to) && (reason == null || reason.isBlank())) throw bad("A reason is required to move an order to " + to + ".");
    }

    /** Core, lock-held status change. Callers own the transaction and the order lock. */
    void apply(AdminPrincipal actor, Order o, String to, String action, String reason, String source, String correlationId, Map<String, Object> attrs) {
        String from = o.getStatus();
        OffsetDateTime now = OffsetDateTime.now();
        if ("ON_HOLD".equals(to) || "CANCELLATION_REQUESTED".equals(to)) o.setStatusBeforeHold(from);
        if ("ON_HOLD".equals(to)) o.setHoldReason(reason);
        if (!"ON_HOLD".equals(to) && "ON_HOLD".equals(from)) o.setHoldReason(null);
        if ("REJECTED".equals(to)) o.setRejectionReason(reason);
        if (attrs != null && attrs.get("prepMinutes") != null) o.setPrepMinutes(((Number) attrs.get("prepMinutes")).intValue());
        o.setStatus(to);
        o.setStatusChangedAt(now);
        String stamp = OrderStateMachine.stampFor(to);
        if ("confirmed".equals(stamp) && o.getConfirmedAt() == null) o.setConfirmedAt(now);
        if ("processing".equals(stamp) && o.getProcessingAt() == null) o.setProcessingAt(now);
        if ("ready".equals(stamp) && o.getReadyAt() == null) o.setReadyAt(now);
        if ("shipped".equals(stamp) && o.getShippedAt() == null) o.setShippedAt(now);
        if ("delivered".equals(stamp)) { o.setDeliveredAt(now); o.setShippingStatus("DELIVERED"); }
        if ("cancelled".equals(stamp)) o.setCancelledAt(now);
        if ("SHIPPED".equals(to)) o.setShippingStatus("SHIPPED");
        if ("OUT_FOR_DELIVERY".equals(to)) o.setShippingStatus("OUT_FOR_DELIVERY");
        if ("CANCELLED".equals(to)) onCancelled(actor, o);
        orders.save(o);
        if (o.getExchangeOfReturnId() != null && exchanges != null && Set.of("SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED").contains(to)) exchanges.onReplacementDispatched(o.getTenantId(), o.getId());
        jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, actor_id, actor_email, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                o.getTenantId(), o.getId(), from, to, action, actor == null ? null : actor.userId(), actor == null ? null : actor.email(), source,
                reason == null ? null : reason.substring(0, Math.min(500, reason.length())), correlationId);
        publish("CANCELLED".equals(to) ? "ORDER_CANCELLED" : "CANCELLATION_REQUESTED".equals(to) ? "ORDER_CANCELLATION_REQUESTED" : "ORDER_STATUS_UPDATED", o, reason, correlationId);
    }

    private void onCancelled(AdminPrincipal actor, Order o) {
        Integer shipped = jdbc.queryForObject("SELECT COUNT(*) FROM order_fulfillments WHERE tenant_id=? AND order_id=? AND status IN ('SHIPPED','DELIVERED')", Integer.class, o.getTenantId(), o.getId());
        if (shipped != null && shipped > 0) throw conflict("Part of this order has already shipped; use Returns & Refunds instead of cancelling.");
        // Unshipped allocations are released so nothing stays reserved for a cancelled order.
        jdbc.update("UPDATE order_fulfillments SET status='CANCELLED', updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE tenant_id=? AND order_id=? AND status IN ('ALLOCATED','PACKED')", o.getTenantId(), o.getId());
        for (OrderItem i : o.getItems()) i.setAllocatedQuantity(0);
        o.setFulfillmentStatus("UNFULFILLED");
        if (o.getPromotionRef() != null) { // released in the tenant the promotions were redeemed in (configured or storefront tenant)
            try { pricing.reverse(o.getTenantId(), o.getPromotionRef()); } catch (RuntimeException e) { log.error("Could not release promotions for cancelled order {}: {}", o.getId(), e.getMessage()); }
        }
        if ("PAID".equals(o.getPaymentStatus())) o.setRefundStatus("PENDING"); // the Payment service is asked to refund; only its callback completes it
    }

    private void publish(String type, Order o, String reason, String correlationId) {
        try {
            producer.publishOrderEvent(new OrderEvent(type, o.getId(), o.getUserId(), o.getStatus(), o.getTotalAmount(), reason, correlationId));
        } catch (RuntimeException e) { log.warn("Order event {} for {} could not be queued: {}", type, o.getId(), e.getMessage()); }
    }

    public Map<String, Object> transition(AdminPrincipal actor, long orderId, String to, String reason, Long expectedVersion, String key, String correlationId, Map<String, Object> attrs) {
        if (to == null || to.isBlank()) throw bad("The target status is required.");
        String target = to.trim().toUpperCase(Locale.ROOT);
        return idempotent(actor, key, "TRANSITION", orderId, target + "|" + reason + "|" + expectedVersion + "|" + attrs, () -> {
            Order o = load(actor, orderId, true);
            if (expectedVersion != null && !expectedVersion.equals(o.getVersion())) throw conflict("The order was changed by someone else; reload and try again.");
            assertTransition(actor, o, target, reason, settings.effective(o.getTenantId(), o.getStoreId()), false);
            apply(actor, o, target, "TRANSITION", reason, "ADMIN", correlationId, attrs);
            return summary(actor, o);
        });
    }

    /** Release from hold, or withdraw a cancellation request: returns to the status the order was in before. */
    public Map<String, Object> release(AdminPrincipal actor, long orderId, String reason, String key, String correlationId) {
        return idempotent(actor, key, "RELEASE", orderId, String.valueOf(reason), () -> {
            Order o = load(actor, orderId, true);
            if (!Set.of("ON_HOLD", "CANCELLATION_REQUESTED").contains(o.getStatus())) throw conflict("Only an order on hold or with a cancellation request can be released.");
            require(actor, "ON_HOLD".equals(o.getStatus()) ? "order:release" : "order:cancel");
            String back = o.getStatusBeforeHold();
            if (back == null || !OrderStateMachine.isKnown(o.getOrderType(), back)) throw conflict("The previous status is unknown; move the order explicitly instead.");
            String from = o.getStatus();
            o.setStatus(back); o.setStatusChangedAt(OffsetDateTime.now()); o.setHoldReason(null); o.setStatusBeforeHold(null);
            orders.save(o);
            jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, actor_id, actor_email, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    o.getTenantId(), o.getId(), from, back, "ON_HOLD".equals(from) ? "RELEASE" : "WITHDRAW_CANCELLATION", actor.userId(), actor.email(), "ADMIN", reason, correlationId);
            publish("ORDER_STATUS_UPDATED", o, reason, correlationId);
            return summary(actor, o);
        });
    }

    // ---- restaurant ------------------------------------------------------------------------------------------------

    public Map<String, Object> restaurantAction(AdminPrincipal actor, long orderId, String action, Integer prepMinutes, String reason, String key, String correlationId) {
        Map<String, Object> attrs = new HashMap<>();
        if (prepMinutes != null) {
            if (prepMinutes < 1 || prepMinutes > 240) throw bad("Preparation time must be between 1 and 240 minutes.");
            attrs.put("prepMinutes", prepMinutes);
        }
        String target = switch (action) {
            case "accept" -> "ACCEPTED"; case "reject" -> "REJECTED"; case "start" -> "PREPARING"; case "delay" -> "PREPARATION_DELAYED";
            case "ready" -> "READY_FOR_PICKUP"; case "picked-up" -> "PICKED_UP"; case "pickup-failed" -> "PICKUP_FAILED";
            default -> throw bad("Unknown restaurant action " + action + ".");
        };
        if ("accept".equals(action) && prepMinutes == null) throw bad("Give an estimated preparation time (minutes) when accepting.");
        Order o = load(actor, orderId, false);
        if (!OrderStateMachine.RESTAURANT.equals(o.getOrderType())) throw conflict("This is not a restaurant order.");
        return transition(actor, orderId, target, reason, null, key, correlationId, attrs);
    }

    /** Status change requested through the legacy (non-admin) endpoints: same state machine and history, no per-user permission check (the gateway authenticates those routes). */
    @Transactional
    public Order legacyTransition(long orderId, String to, String reason) {
        Order o = orders.findByIdForUpdate(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + orderId));
        String target = to == null ? "" : to.trim().toUpperCase(Locale.ROOT);
        if (!OrderStateMachine.isKnown(o.getOrderType(), target)) throw conflict("Unknown status '" + to + "' for a " + o.getOrderType() + " order.");
        if (!OrderStateMachine.canMove(o.getOrderType(), o.getStatus(), target))
            throw conflict("Cannot move order " + orderNumber(o.getId()) + " from " + o.getStatus() + " to " + target + ". Allowed next: " + OrderStateMachine.allowed(o.getOrderType(), o.getStatus()));
        if (OrderStateMachine.SYSTEM_ONLY_TARGETS.contains(target) || FULFILLMENT_DRIVEN.contains(target)) throw conflict(target + " is set by the Payment/Fulfillment process, not directly.");
        apply(null, o, target, "TRANSITION", reason != null ? reason : OrderStateMachine.REASON_REQUIRED.contains(target) ? "Requested through the order API" : null, "LEGACY", UUID.randomUUID().toString(), null);
        return o;
    }

    /** Called after Delivery Service progress was applied to an order: keeps the lifecycle status, history and delivery reference in step. */
    @Transactional
    public void recordDelivery(long orderId, String statusBefore, String deliveryStatus) {
        Order o = orders.findByIdForUpdate(orderId).orElse(null);
        if (o == null) return;
        String target = switch (deliveryStatus) { case "PICKED_UP" -> "SHIPPED"; case "IN_TRANSIT" -> "OUT_FOR_DELIVERY"; case "DELIVERED" -> "DELIVERED"; case "DELIVERY_FAILED" -> "DELIVERY_FAILED"; default -> null; };
        if (target == null) return;
        String correlation = UUID.randomUUID().toString();
        if (!target.equals(statusBefore) && !target.equals(o.getStatus())) {
            if (OrderStateMachine.canMove(o.getOrderType(), o.getStatus(), target)) { apply(null, o, target, "DELIVERY_" + deliveryStatus, "Delivery service report", "DELIVERY", correlation, null); return; }
        }
        if (!target.equals(statusBefore)) {
            // The legacy code path already moved the order; record that fact without re-applying it.
            o.setStatusChangedAt(OffsetDateTime.now());
            if ("DELIVERED".equals(target) && o.getDeliveredAt() == null) o.setDeliveredAt(OffsetDateTime.now());
            orders.save(o);
            jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?)",
                    o.getTenantId(), o.getId(), statusBefore, o.getStatus(), "DELIVERY_" + deliveryStatus, "DELIVERY", "Delivery service report", correlation);
        }
    }

    // ---- notes, assignment -----------------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> addNote(AdminPrincipal actor, long orderId, String kind, String body) {
        require(actor, "order:note:create");
        Order o = load(actor, orderId, false);
        String k = kind == null ? "INTERNAL" : kind.toUpperCase(Locale.ROOT);
        if (!Set.of("INTERNAL", "COMMUNICATION", "ADMIN_COMMENT").contains(k)) throw bad("kind must be INTERNAL, COMMUNICATION or ADMIN_COMMENT.");
        if (body == null || body.isBlank()) throw bad("The note cannot be empty.");
        if (body.length() > 2000) throw bad("Notes are limited to 2000 characters.");
        jdbc.update("INSERT INTO order_notes(tenant_id, order_id, kind, body, author_id, author_email) VALUES (?,?,?,?,?,?)", o.getTenantId(), o.getId(), k, body.strip(), actor.userId(), actor.email());
        return Map.of("notes", notes(actor, o.getId()));
    }

    public List<Map<String, Object>> notes(AdminPrincipal actor, long orderId) {
        return support.rows("SELECT id, kind, body, author_id, author_email, created_at FROM order_notes WHERE tenant_id=? AND order_id=? ORDER BY created_at, id", actor.tenantId(), orderId);
    }

    @Transactional
    public Map<String, Object> assignOperator(AdminPrincipal actor, long orderId, Long operatorId) {
        require(actor, "order:process");
        Order o = load(actor, orderId, true);
        if (operatorId != null && operatorId <= 0) throw bad("operatorId must be positive.");
        Long before = o.getAssignedOperatorId();
        o.setAssignedOperatorId(operatorId);
        orders.save(o);
        jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, actor_id, actor_email, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                o.getTenantId(), o.getId(), o.getStatus(), o.getStatus(), "ASSIGN_OPERATOR", actor.userId(), actor.email(), "ADMIN", "operator " + before + " -> " + operatorId, UUID.randomUUID().toString());
        return summary(actor, o);
    }

    // ---- inbound domain events (payment / refund) ---------------------------------------------------------------------

    /** Payment Service callback. Idempotent per event id; the only way an order becomes PAID. */
    public Map<String, Object> onPaymentEvent(UUID tenant, String eventId, long orderId, String status, String method, String reference) {
        if (eventId == null || eventId.isBlank()) throw bad("eventId is required.");
        if (!Set.of("PENDING", "PAID", "FAILED").contains(status)) throw bad("status must be PENDING, PAID or FAILED.");
        return processOnce(tenant, "PAYMENT", eventId, orderId, () -> {
            Order o = orders.findByIdForUpdate(orderId).filter(x -> x.getTenantId().equals(tenant)).orElseThrow(() -> notFound("Order"));
            if ("PAID".equals(o.getPaymentStatus()) && !"PAID".equals(status)) throw conflict("The order is already paid; a late failure event is ignored.");
            o.setPaymentStatus(status); o.setPaymentMethod(method); o.setPaymentReference(reference);
            String correlation = UUID.randomUUID().toString();
            String target = switch (status) { case "PAID" -> "PAYMENT_CONFIRMED"; case "FAILED" -> "PAYMENT_FAILED"; default -> "PAYMENT_PENDING"; };
            if (OrderStateMachine.canMove(o.getOrderType(), o.getStatus(), target)) apply(null, o, target, "PAYMENT_" + status, "Payment service event " + eventId, "PAYMENT", correlation, null);
            else {
                orders.save(o);
                jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, actor_id, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?,?)",
                        o.getTenantId(), o.getId(), o.getStatus(), o.getStatus(), "PAYMENT_" + status, null, "PAYMENT", "Payment service event " + eventId + " (status unchanged)", correlation);
            }
            return Map.of("orderId", orderId, "paymentStatus", status, "status", o.getStatus());
        });
    }

    /** Payment/Accounting confirmation that a refund completed: the only path to REFUNDED. */
    public Map<String, Object> onRefundEvent(UUID tenant, String eventId, long orderId, String status, String reference) {
        if (eventId == null || eventId.isBlank()) throw bad("eventId is required.");
        if (!Set.of("COMPLETED", "FAILED").contains(status)) throw bad("status must be COMPLETED or FAILED.");
        return processOnce(tenant, "REFUND", eventId, orderId, () -> {
            Order o = orders.findByIdForUpdate(orderId).filter(x -> x.getTenantId().equals(tenant)).orElseThrow(() -> notFound("Order"));
            String correlation = UUID.randomUUID().toString();
            if ("FAILED".equals(status)) {
                o.setRefundStatus("FAILED"); orders.save(o);
                jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?)",
                        o.getTenantId(), o.getId(), o.getStatus(), o.getStatus(), "REFUND_FAILED", "PAYMENT", "Refund failed: " + reference, correlation);
                return Map.of("orderId", orderId, "refundStatus", "FAILED", "status", o.getStatus());
            }
            if (!Set.of("CANCELLED", "RETURN_REQUESTED", "REFUND_PENDING").contains(o.getStatus())) throw conflict("A refund can only be confirmed for a cancelled, returned or refund-pending order.");
            if ("CANCELLED".equals(o.getStatus()) || "RETURN_REQUESTED".equals(o.getStatus())) apply(null, o, "REFUND_PENDING", "REFUND_REQUESTED", "Refund in progress", "PAYMENT", correlation, null);
            o.setRefundStatus("COMPLETED"); o.setRefundReference(reference); o.setPaymentStatus("REFUNDED");
            apply(null, o, "REFUNDED", "REFUND_CONFIRMED", "Refund confirmed by Payment service: " + reference, "PAYMENT", correlation, null);
            return Map.of("orderId", orderId, "refundStatus", "COMPLETED", "status", o.getStatus());
        });
    }

    /** Applies an inbound event exactly once. A repeated event id returns the first outcome's acknowledgement without side effects. */
    private Map<String, Object> processOnce(UUID tenant, String source, String eventId, long orderId, Supplier<Map<String, Object>> work) {
        return idempotency.once(tenant, source, eventId, orderId, work);
    }

    // ---- details ------------------------------------------------------------------------------------------------------

    public Map<String, Object> summary(AdminPrincipal actor, Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("orderNumber", orderNumber(o.getId())); m.put("status", o.getStatus()); m.put("version", o.getVersion());
        m.put("allowedTransitions", allowedTransitions(actor, o, settings.effective(o.getTenantId(), o.getStoreId())));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> details(AdminPrincipal actor, long id) {
        Order o = load(actor, id, false);
        OrderSettingsService.Settings cfg = settings.effective(o.getTenantId(), o.getStoreId());
        boolean pii = actor.hasPermission("order:customer:view");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", o.getId()); order.put("orderNumber", orderNumber(o.getId())); order.put("type", o.getOrderType()); order.put("status", o.getStatus());
        order.put("storeId", o.getStoreId()); order.put("vendorId", o.getVendorId()); order.put("createdAt", o.getCreatedAt()); order.put("statusChangedAt", o.getStatusChangedAt());
        order.put("version", o.getVersion()); order.put("holdReason", o.getHoldReason()); order.put("rejectionReason", o.getRejectionReason());
        order.put("prepMinutes", o.getPrepMinutes()); order.put("specialInstructions", o.getSpecialInstructions()); order.put("assignedOperatorId", o.getAssignedOperatorId());
        order.put("couponCode", o.getCouponCode());
        m.put("order", order);
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("hidden", !pii);
        if (pii) {
            customer.put("userId", o.getUserId()); customer.put("name", o.getCustomerName()); customer.put("email", o.getCustomerEmail()); customer.put("phone", o.getCustomerPhone());
            customer.put("deliveryAddress", o.getDeliveryAddress());
            customer.put("previousOrders", o.getUserId() == null ? 0 : orders.count((root, q, cb) -> cb.and(cb.equal(root.get("tenantId"), o.getTenantId()), cb.equal(root.get("userId"), o.getUserId()))) - 1);
        }
        m.put("customer", customer);
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem i : o.getItems()) {
            BigDecimal line = i.getPrice().multiply(BigDecimal.valueOf(OrderQueryService.quantity(i)));
            subtotal = subtotal.add(line);
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", i.getId()); im.put("productId", i.getProductId()); im.put("name", i.getProductName()); im.put("unitPrice", i.getPrice()); im.put("quantity", i.getQuantity());
            im.put("lineTotal", line); im.put("vendorId", i.getVendorId()); im.put("allocatedQuantity", i.getAllocatedQuantity()); im.put("fulfilledQuantity", i.getFulfilledQuantity());
            items.add(im);
        }
        m.put("items", items);
        Map<String, Object> amounts = new LinkedHashMap<>();
        amounts.put("subtotal", subtotal); amounts.put("discount", o.getDiscountAmount()); amounts.put("tax", o.getTaxAmount()); amounts.put("deliveryCharge", o.getDeliveryCharge()); amounts.put("payable", o.getTotalAmount());
        m.put("amounts", amounts);
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("status", o.getPaymentStatus()); payment.put("method", o.getPaymentMethod()); payment.put("reference", o.getPaymentReference());
        payment.put("refundStatus", o.getRefundStatus()); payment.put("refundReference", o.getRefundReference());
        payment.put("history", support.rows("SELECT action, to_status, source, reason, created_at FROM order_status_history WHERE tenant_id=? AND order_id=? AND (action LIKE 'PAYMENT%' OR action LIKE 'REFUND%') ORDER BY created_at, id", o.getTenantId(), o.getId()));
        payment.put("attempts", ((List<?>) payment.get("history")).stream().filter(h -> String.valueOf(((Map<?, ?>) h).get("action")).startsWith("PAYMENT_")).count());
        m.put("payment", payment);
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("status", o.getShippingStatus()); delivery.put("carrier", o.getCarrier()); delivery.put("trackingNumber", o.getTrackingNumber()); delivery.put("partner", o.getDeliveryPartnerName());
        delivery.put("deliveryReference", o.getDeliveryReference()); delivery.put("estimatedDelivery", o.getEstimatedDelivery());
        m.put("delivery", delivery);
        m.put("fulfillmentStatus", o.getFulfillmentStatus());
        m.put("sla", slaOf(o, cfg));
        m.put("allowedTransitions", allowedTransitions(actor, o, cfg));
        m.put("returns", returns.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream().map(this::returnView).toList());
        m.put("notes", notes(actor, o.getId()));
        return m;
    }

    private Map<String, Object> slaOf(Order o, OrderSettingsService.Settings cfg) {
        SlaCalculator.Result r = SlaCalculator.evaluate(o.getOrderType(), o.getStatus(), o.getStatusChangedAt(), OffsetDateTime.now(), cfg.sla());
        return Map.of("indicator", r.indicator().name(), "phase", String.valueOf(r.phase()), "elapsedMinutes", r.elapsedMinutes(), "thresholdMinutes", r.thresholdMinutes());
    }

    Map<String, Object> returnView(OrderReturn r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId()); m.put("status", r.getStatus()); m.put("reason", r.getReason()); m.put("refundAmount", r.getRefundAmount()); m.put("createdAt", r.getCreatedAt());
        m.put("resolvedAt", r.getResolvedAt());
        m.put("items", r.getItems().stream().map(i -> Map.of("orderItemId", i.getOrderItemId(), "productId", i.getProductId(), "quantity", i.getQuantity())).toList());
        m.put("refundConfirmed", "REFUNDED".equals(r.getStatus()));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> timeline(AdminPrincipal actor, long id) {
        Order o = load(actor, id, false);
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(Map.of("at", o.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(), "type", "ORDER_CREATED", "label", "Order created", "source", "SYSTEM"));
        for (Map<String, Object> h : support.rows("SELECT * FROM order_status_history WHERE tenant_id=? AND order_id=? ORDER BY created_at, id", o.getTenantId(), o.getId())) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("at", h.get("createdAt")); e.put("type", h.get("action")); e.put("label", label(String.valueOf(h.get("action")), String.valueOf(h.get("toStatus"))));
            e.put("from", h.get("fromStatus")); e.put("to", h.get("toStatus")); e.put("actorId", h.get("actorId")); e.put("actor", h.get("actorEmail")); e.put("source", h.get("source"));
            e.put("reason", h.get("reason")); e.put("correlationId", h.get("correlationId"));
            events.add(e);
        }
        for (var t : tracking.findByOrderIdOrderByEventTimestampAsc(o.getId())) {
            events.add(new LinkedHashMap<>(Map.of("at", t.getEventTimestamp(), "type", "TRACKING", "label", "Shipment: " + t.getStatus(), "source", "DELIVERY",
                    "reason", String.valueOf(t.getDescription() == null ? "" : t.getDescription()))));
        }
        for (Map<String, Object> f : support.rows("SELECT id, location_name, status, carrier, tracking_reference, created_at, shipped_at FROM order_fulfillments WHERE tenant_id=? AND order_id=?", o.getTenantId(), o.getId())) {
            events.add(new LinkedHashMap<>(Map.of("at", f.get("createdAt"), "type", "FULFILLMENT_CREATED", "label", "Fulfillment #" + f.get("id") + " created at " + f.get("locationName"), "source", "ADMIN")));
            if (f.get("shippedAt") != null) events.add(new LinkedHashMap<>(Map.of("at", f.get("shippedAt"), "type", "SHIPMENT_CREATED", "label", "Shipment created" + (f.get("trackingReference") == null ? "" : " (" + f.get("trackingReference") + ")"), "source", "ADMIN")));
        }
        events.sort(Comparator.comparing(e -> instant(e.get("at"))));
        return Map.of("orderId", o.getId(), "events", events);
    }

    static java.time.Instant instant(Object v) {
        if (v instanceof OffsetDateTime o) return o.toInstant();
        if (v instanceof java.time.LocalDateTime l) return l.atZone(java.time.ZoneId.systemDefault()).toInstant();
        if (v instanceof java.sql.Timestamp t) return t.toInstant();
        return java.time.Instant.EPOCH;
    }

    private static String label(String action, String to) {
        return switch (action) {
            case "PAYMENT_PAID" -> "Payment confirmed"; case "PAYMENT_PENDING" -> "Payment initiated"; case "PAYMENT_FAILED" -> "Payment failed";
            case "HOLD", "TRANSITION" -> "Status: " + to; case "RELEASE" -> "Released from hold"; case "WITHDRAW_CANCELLATION" -> "Cancellation request withdrawn";
            case "REFUND_REQUESTED" -> "Refund requested"; case "REFUND_CONFIRMED" -> "Refund confirmed"; case "ASSIGN_OPERATOR" -> "Operator assigned";
            default -> action.replace('_', ' ').toLowerCase(Locale.ROOT) + ": " + to;
        };
    }

    /** Org-wide audit trail of order changes, newest first, filterable. */
    public List<Map<String, Object>> audit(AdminPrincipal actor, Long orderId, String action, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM order_status_history WHERE tenant_id=?");
        List<Object> args = new ArrayList<>(List.of(actor.tenantId()));
        if (orderId != null) { sql.append(" AND order_id=?"); args.add(orderId); }
        if (action != null && !action.isBlank()) { sql.append(" AND action=?"); args.add(action); }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ").append(Math.max(1, Math.min(limit, 500)));
        List<Map<String, Object>> rows = support.rows(sql.toString(), args.toArray());
        if (!actor.hasPermission("order:view-all-stores")) rows.removeIf(r -> orders.findById(((Number) r.get("orderId")).longValue()).map(o -> !actor.canSeeStore(o.getStoreId())).orElse(true));
        rows.forEach(r -> r.put("orderNumber", orderNumber(((Number) r.get("orderId")).longValue())));
        return rows;
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Map<String, Object> parse(String s) { try { return mapper.readValue(s, new TypeReference<Map<String, Object>>() {}); } catch (Exception e) { return Map.of(); } }

    static String sha(String s) {
        try { StringBuilder sb = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b)); return sb.toString(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}

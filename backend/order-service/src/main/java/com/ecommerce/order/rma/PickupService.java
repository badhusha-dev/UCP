package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.IdempotencyService;
import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Reverse logistics. This service asks the Delivery side to collect the goods (an event carrying the pickup reference) and records
 * the progress Delivery reports back through an authenticated callback; it does not run its own delivery operation. Restaurant
 * claims never have a pickup. Out-of-order or repeated progress reports are ignored, never rolled back.
 */
@Slf4j
@Service
public class PickupService {
    private static final List<String> ORDER = List.of("REQUESTED", "SCHEDULED", "PICKED_UP", "IN_TRANSIT", "AT_WAREHOUSE");

    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final OrderRepository orders;
    private final ReturnService returns;
    private final ReturnExceptionService exceptions;
    private final IdempotencyService idempotency;
    private final OrderProducerService producer;

    public PickupService(JdbcTemplate jdbc, AdminSupport support, OrderRepository orders, ReturnService returns, ReturnExceptionService exceptions, IdempotencyService idempotency, OrderProducerService producer) {
        this.jdbc = jdbc; this.support = support; this.orders = orders; this.returns = returns; this.exceptions = exceptions; this.idempotency = idempotency; this.producer = producer;
    }

    public record Request(String address, String phone, OffsetDateTime from, OffsetDateTime to, String destination) {}

    /** Schedules (or reschedules after a failed attempt) the reverse pickup and moves the return to PICKUP_SCHEDULED. */
    @Transactional
    public Map<String, Object> schedule(AdminPrincipal admin, long returnId, Request req) {
        Map<String, Object> r = returns.lock(admin, returnId);
        if (ReturnStateMachine.CLAIM.equals(r.get("returnKind"))) throw conflict("Restaurant claims have no reverse pickup.");
        String status = (String) r.get("status");
        if (!Set.of("APPROVED", "PICKUP_FAILED").contains(status)) throw conflict("A pickup can be scheduled only for an approved return or after a failed attempt (it is " + status + ").");
        if (req.from() == null || req.to() == null || !req.to().isAfter(req.from())) throw bad("Provide a pickup window where 'to' is after 'from'.");
        if (req.from().isBefore(OffsetDateTime.now().minusMinutes(5))) throw bad("The pickup window cannot start in the past.");
        Order order = orders.findById(((Number) r.get("orderId")).longValue()).orElseThrow(() -> notFound("Order"));
        String address = req.address() == null || req.address().isBlank() ? order.getDeliveryAddress() : req.address().strip();
        if (address == null || address.isBlank()) throw bad("A pickup address is required.");
        if (address.length() > 500 || (req.phone() != null && req.phone().length() > 40) || (req.destination() != null && req.destination().length() > 160)) throw bad("Address, phone or destination is too long.");
        Map<String, Object> existing = support.rows("SELECT * FROM reverse_pickup_requests WHERE tenant_id=? AND return_id=? AND status NOT IN ('CANCELLED') ORDER BY id DESC", admin.tenantId(), returnId).stream().findFirst().orElse(null);
        long pickupId;
        if (existing != null && "FAILED".equals(existing.get("status"))) {
            pickupId = ((Number) existing.get("id")).longValue();
            jdbc.update("UPDATE reverse_pickup_requests SET pickup_address=?, contact_phone=?, scheduled_from=?, scheduled_to=?, status='SCHEDULED', failure_reason=NULL, destination=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    address, req.phone(), Timestamp.from(req.from().toInstant()), Timestamp.from(req.to().toInstant()), req.destination(), pickupId);
        } else if (existing != null) throw conflict("This return already has an active pickup (" + existing.get("status") + ").");
        else {
            jdbc.update("INSERT INTO reverse_pickup_requests(tenant_id, return_id, pickup_address, contact_phone, scheduled_from, scheduled_to, status, destination, created_by) VALUES (?,?,?,?,?,?,'SCHEDULED',?,?)",
                    admin.tenantId(), returnId, address, req.phone(), Timestamp.from(req.from().toInstant()), Timestamp.from(req.to().toInstant()), req.destination(), admin.userId());
            pickupId = jdbc.queryForObject("SELECT MAX(id) FROM reverse_pickup_requests WHERE tenant_id=? AND return_id=?", Long.class, admin.tenantId(), returnId);
        }
        String ref = "RP-" + pickupId;
        jdbc.update("UPDATE reverse_pickup_requests SET delivery_reference=? WHERE id=?", ref, pickupId);
        returns.move(admin.tenantId(), returnId, "PICKUP_SCHEDULED", ReturnService.Actor.of(admin), "Pickup scheduled " + req.from() + " - " + req.to() + ".", "PICKUP_SCHEDULE");
        returns.audit(admin.tenantId(), returnId, "PICKUP", String.valueOf(pickupId), "SCHEDULE", ReturnService.Actor.of(admin), ref);
        try {
            producer.publishOrderEvent(new OrderEvent("RETURN_PICKUP_REQUESTED", order.getId(), order.getUserId(), "PICKUP_SCHEDULED", null, address, ref));
        } catch (RuntimeException e) { log.warn("Pickup request event {} could not be published: {}", ref, e.getMessage()); }
        return support.one("SELECT * FROM reverse_pickup_requests WHERE id=?", "Pickup", pickupId);
    }

    @Transactional
    public Map<String, Object> cancel(AdminPrincipal admin, long returnId, String reason) {
        returns.lock(admin, returnId);
        if (reason == null || reason.isBlank()) throw bad("A reason is required.");
        List<Map<String, Object>> active = support.rows("SELECT * FROM reverse_pickup_requests WHERE tenant_id=? AND return_id=? AND status IN ('REQUESTED','SCHEDULED') ORDER BY id DESC", admin.tenantId(), returnId);
        if (active.isEmpty()) throw conflict("There is no pickup that can still be cancelled.");
        jdbc.update("UPDATE reverse_pickup_requests SET status='CANCELLED', failure_reason=?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE id=?", ReturnService.clip(reason, 255), active.get(0).get("id"));
        returns.audit(admin.tenantId(), returnId, "PICKUP", String.valueOf(active.get(0).get("id")), "CANCEL", ReturnService.Actor.of(admin), reason);
        return Map.of("cancelled", true);
    }

    /** Delivery-service progress report. Idempotent by event id; reports behind the current state are ignored. */
    public Map<String, Object> progress(UUID tenant, String eventId, String pickupReference, String status, String partner, String tracking, String proof, String failureReason) {
        if (eventId == null || !eventId.matches("[A-Za-z0-9_.:-]{6,100}")) throw bad("eventId is required.");
        if (!Set.of("PICKED_UP", "IN_TRANSIT", "AT_WAREHOUSE", "FAILED", "LOST").contains(status)) throw bad("status must be PICKED_UP, IN_TRANSIT, AT_WAREHOUSE, FAILED or LOST.");
        Map<String, Object> p = support.one("SELECT * FROM reverse_pickup_requests WHERE tenant_id=? AND delivery_reference=?", "Pickup", tenant, pickupReference);
        long returnId = ((Number) p.get("returnId")).longValue();
        return idempotency.once(tenant, "DELIVERY-PICKUP", eventId, ((Number) support.one("SELECT order_id FROM order_return WHERE id=?", "Return", returnId).get("orderId")).longValue(), () -> applyProgress(tenant, p, returnId, status, partner, tracking, proof, failureReason));
    }

    private Map<String, Object> applyProgress(UUID tenant, Map<String, Object> p, long returnId, String status, String partner, String tracking, String proof, String failureReason) {
        long pickupId = ((Number) p.get("id")).longValue();
        jdbc.query("SELECT id FROM order_return WHERE id=? AND tenant_id=? FOR UPDATE", rs -> {}, returnId, tenant);
        String current = (String) support.one("SELECT status FROM reverse_pickup_requests WHERE id=?", "Pickup", pickupId).get("status");
        String returnStatus = (String) support.one("SELECT status FROM order_return WHERE id=?", "Return", returnId).get("status");
        ReturnService.Actor delivery = ReturnService.Actor.system("DELIVERY");
        if (Set.of("CANCELLED").contains(current)) return Map.of("pickupId", pickupId, "ignored", "Pickup was cancelled.");
        jdbc.update("UPDATE reverse_pickup_requests SET partner_name=COALESCE(?, partner_name), tracking_reference=COALESCE(?, tracking_reference), proof_reference=COALESCE(?, proof_reference), updated_at=CURRENT_TIMESTAMP WHERE id=?",
                ReturnService.clip(partner, 160), ReturnService.clip(tracking, 128), ReturnService.clip(proof, 160), pickupId);
        switch (status) {
            case "FAILED" -> {
                if (!Set.of("SCHEDULED", "REQUESTED").contains(current)) return Map.of("pickupId", pickupId, "ignored", "Pickup is already " + current + ".");
                jdbc.update("UPDATE reverse_pickup_requests SET status='FAILED', attempts=attempts+1, failure_reason=?, version=version+1 WHERE id=?", ReturnService.clip(failureReason == null ? "Pickup attempt failed." : failureReason, 255), pickupId);
                if (returnStatus.equals("PICKUP_SCHEDULED")) returns.move(tenant, returnId, "PICKUP_FAILED", delivery, failureReason == null || failureReason.isBlank() ? "Pickup attempt failed." : failureReason, "PICKUP_FAILED");
                exceptions.scan(tenant, returnId);
                return Map.of("pickupId", pickupId, "status", "FAILED");
            }
            case "LOST" -> {
                if (!Set.of("PICKED_UP", "IN_TRANSIT").contains(returnStatus)) return Map.of("pickupId", pickupId, "ignored", "Nothing is in transit.");
                returns.move(tenant, returnId, "RETURN_LOST", delivery, failureReason == null || failureReason.isBlank() ? "Carrier reported the parcel lost." : failureReason, "RETURN_LOST");
                exceptions.raise(tenant, returnId, "LOST_SHIPMENT", "HIGH", "Carrier reported the return parcel lost.", null, "carrier");
                return Map.of("pickupId", pickupId, "status", returnStatus);
            }
            default -> {
                if (ORDER.indexOf(status) <= ORDER.indexOf(current)) return Map.of("pickupId", pickupId, "ignored", "Already " + current + ".");
                jdbc.update("UPDATE reverse_pickup_requests SET status=?, version=version+1 WHERE id=?", status, pickupId);
                String walk = returnStatus;
                for (String step : List.of("PICKED_UP", "IN_TRANSIT", "RECEIVED")) {
                    String target = step.equals("RECEIVED") ? (status.equals("AT_WAREHOUSE") ? "RECEIVED" : null) : step;
                    if (target == null) continue;
                    if (rank(target) <= rank(walk)) continue;
                    if (rank(target) > rank(status.equals("AT_WAREHOUSE") ? "RECEIVED" : status)) break;
                    if (!ReturnStateMachine.canMove(ReturnStateMachine.RETAIL, walk, target)) continue;
                    returns.move(tenant, returnId, target, delivery, "Delivery reported " + status + ".", "PICKUP_" + status);
                    walk = target;
                }
                if (status.equals("AT_WAREHOUSE")) {
                    jdbc.update("UPDATE order_return SET received_at=COALESCE(received_at, CURRENT_TIMESTAMP) WHERE id=?", returnId);
                    jdbc.update("UPDATE order_return_items SET received_quantity=COALESCE(approved_quantity, quantity) WHERE return_id=? AND received_quantity IS NULL", returnId);
                }
                return Map.of("pickupId", pickupId, "status", status);
            }
        }
    }

    private static int rank(String s) {
        return switch (s) { case "PICKUP_SCHEDULED" -> 1; case "PICKED_UP" -> 2; case "IN_TRANSIT" -> 3; case "RECEIVED", "AT_WAREHOUSE" -> 4; default -> 0; };
    }

    public List<Map<String, Object>> list(AdminPrincipal admin, long returnId) {
        returns.load(admin, returnId);
        return support.rows("SELECT * FROM reverse_pickup_requests WHERE tenant_id=? AND return_id=? ORDER BY id DESC", admin.tenantId(), returnId);
    }
}

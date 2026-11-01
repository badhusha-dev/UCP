package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Item-level allocation, partial fulfillment and split shipments for retail orders. The order row lock taken before every change
 * serialises concurrent allocations, and quantities are checked against what was ordered so an item can never be over-allocated.
 * Restaurant orders have their own kitchen workflow and are deliberately excluded.
 */
@Service
public class FulfillmentService {
    public record Line(Long orderItemId, Integer quantity) {}
    public record Request(String locationCode, String locationName, String method, List<Line> lines) {}

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    private final OrderRepository orders;
    private final OrderAdminService admin;
    private final JdbcTemplate jdbc;
    private final AdminSupport support;
    private final RestTemplate restTemplate;

    @Value("${services.delivery.url:http://localhost:8091}")
    private String deliveryServiceUrl;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToServiceToken;

    public FulfillmentService(OrderRepository orders, OrderAdminService admin, JdbcTemplate jdbc, AdminSupport support, RestTemplate restTemplate) { this.orders = orders; this.admin = admin; this.jdbc = jdbc; this.support = support; this.restTemplate = restTemplate; }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(AdminPrincipal actor, long orderId) {
        require(actor, "order:fulfillment:view");
        Order o = admin.load(actor, orderId, false);
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderItem i : o.getItems()) {
            int q = OrderQueryService.quantity(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderItemId", i.getId()); m.put("name", i.getProductName()); m.put("vendorId", i.getVendorId()); m.put("ordered", q);
            m.put("allocated", i.getAllocatedQuantity()); m.put("fulfilled", i.getFulfilledQuantity()); m.put("unallocated", q - i.getAllocatedQuantity());
            items.add(m);
        }
        List<Map<String, Object>> fulfillments = support.rows("SELECT * FROM order_fulfillments WHERE tenant_id=? AND order_id=? ORDER BY id", o.getTenantId(), o.getId());
        for (Map<String, Object> f : fulfillments)
            f.put("items", support.rows("SELECT order_item_id, quantity FROM order_fulfillment_items WHERE tenant_id=? AND fulfillment_id=? ORDER BY id", o.getTenantId(), f.get("id")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderType", o.getOrderType()); out.put("status", o.getStatus()); out.put("fulfillmentStatus", o.getFulfillmentStatus());
        out.put("applicable", !OrderStateMachine.RESTAURANT.equals(o.getOrderType()));
        out.put("items", items); out.put("fulfillments", fulfillments);
        out.put("delivery", Map.of("status", String.valueOf(o.getShippingStatus()), "partner", String.valueOf(o.getDeliveryPartnerName()), "reference", String.valueOf(o.getDeliveryReference())));
        return out;
    }

    public Map<String, Object> create(AdminPrincipal actor, long orderId, Request r, String key, String correlationId) {
        require(actor, "order:process");
        return admin.idempotent(actor, key, "FULFILLMENT_CREATE", orderId, String.valueOf(r), () -> {
            Order o = admin.load(actor, orderId, true);
            if (OrderStateMachine.RESTAURANT.equals(o.getOrderType())) throw conflict("Restaurant orders use the kitchen workflow, not warehouse fulfillment.");
            if (!Set.of("READY_FOR_FULFILLMENT", "PARTIALLY_FULFILLED").contains(o.getStatus())) throw conflict("Fulfillments can be created when the order is READY_FOR_FULFILLMENT or PARTIALLY_FULFILLED (now " + o.getStatus() + ").");
            if (r.locationCode() == null || !r.locationCode().matches("[A-Za-z0-9_-]{1,64}")) throw bad("locationCode must be 1-64 letters, digits, '_' or '-'.");
            String method = r.method() == null ? "SHIP" : r.method();
            if (!Set.of("SHIP", "PICKUP", "DELIVERY").contains(method)) throw bad("method must be SHIP, PICKUP or DELIVERY.");
            String locationName = r.locationName() == null ? null : ContentChecks.plain(r.locationName(), "locationName", 160);
            if (r.lines() == null || r.lines().isEmpty()) throw bad("Allocate at least one item.");
            Map<Long, OrderItem> byId = new HashMap<>();
            o.getItems().forEach(i -> byId.put(i.getId(), i));
            Set<Long> seen = new HashSet<>();
            Set<String> vendors = new HashSet<>();
            for (Line l : r.lines()) {
                if (l.orderItemId() == null || !byId.containsKey(l.orderItemId())) throw bad("Item " + l.orderItemId() + " is not part of this order.");
                if (!seen.add(l.orderItemId())) throw bad("Item " + l.orderItemId() + " appears twice.");
                if (l.quantity() == null || l.quantity() <= 0) throw bad("Quantities must be greater than zero.");
                OrderItem item = byId.get(l.orderItemId());
                int open = OrderQueryService.quantity(item) - item.getAllocatedQuantity();
                if (l.quantity() > open) throw conflict("Cannot allocate " + l.quantity() + " of '" + item.getProductName() + "': only " + open + " of " + OrderQueryService.quantity(item) + " remain unallocated.");
                vendors.add(String.valueOf(item.getVendorId()));
            }
            if (vendors.size() > 1) throw bad("One fulfillment can only contain items of a single vendor; create one per vendor.");
            jdbc.update("INSERT INTO order_fulfillments(tenant_id, order_id, location_code, location_name, method, created_by) VALUES (?,?,?,?,?,?)", o.getTenantId(), o.getId(), r.locationCode(), locationName, method, actor.userId());
            long fid = jdbc.queryForObject("SELECT MAX(id) FROM order_fulfillments WHERE tenant_id=? AND order_id=?", Long.class, o.getTenantId(), o.getId());
            for (Line l : r.lines()) {
                jdbc.update("INSERT INTO order_fulfillment_items(tenant_id, fulfillment_id, order_item_id, quantity) VALUES (?,?,?,?)", o.getTenantId(), fid, l.orderItemId(), l.quantity());
                OrderItem item = byId.get(l.orderItemId());
                item.setAllocatedQuantity(item.getAllocatedQuantity() + l.quantity());
            }
            refreshOrderFulfillmentStatus(o);
            orders.save(o);
            jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, actor_id, actor_email, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    o.getTenantId(), o.getId(), o.getStatus(), o.getStatus(), "FULFILLMENT_ALLOCATED", actor.userId(), actor.email(), "ADMIN", "fulfillment " + fid + " at " + r.locationCode(), correlationId);
            return Map.of("fulfillmentId", fid, "fulfillmentStatus", o.getFulfillmentStatus());
        });
    }

    @Transactional
    public Map<String, Object> packing(AdminPrincipal actor, long fulfillmentId, String packing) {
        require(actor, "order:process");
        if (!Set.of("NOT_STARTED", "PACKING", "PACKED").contains(packing)) throw bad("packingStatus must be NOT_STARTED, PACKING or PACKED.");
        Map<String, Object> f = fulfillment(actor, fulfillmentId);
        Order o = admin.load(actor, ((Number) f.get("orderId")).longValue(), true);
        if (!Set.of("ALLOCATED", "PACKED").contains(f.get("status"))) throw conflict("Only an open fulfillment can be packed.");
        jdbc.update("UPDATE order_fulfillments SET packing_status=?, status=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=? AND tenant_id=?", packing, "PACKED".equals(packing) ? "PACKED" : "ALLOCATED", fulfillmentId, o.getTenantId());
        return Map.of("fulfillmentId", fulfillmentId, "packingStatus", packing);
    }

    /** Marks a fulfillment shipped (or picked up). Drives the order to PARTIALLY_FULFILLED, or to SHIPPED/FULFILLED once every item is out. */
    public Map<String, Object> ship(AdminPrincipal actor, long fulfillmentId, String carrier, String tracking, String key, String correlationId) {
        require(actor, "order:process");
        Map<String, Object> f0 = fulfillment(actor, fulfillmentId);
        long orderId = ((Number) f0.get("orderId")).longValue();
        Map<String, Object> result = admin.idempotent(actor, key, "FULFILLMENT_SHIP", orderId, fulfillmentId + "|" + carrier + "|" + tracking, () -> {
            Order o = admin.load(actor, orderId, true);
            Map<String, Object> f = fulfillment(actor, fulfillmentId);
            if (!Set.of("ALLOCATED", "PACKED").contains(f.get("status"))) throw conflict("This fulfillment is " + f.get("status") + " and cannot be shipped.");
            String method = (String) f.get("method");
            String c = carrier == null || carrier.isBlank() ? null : ContentChecks.plain(carrier, "carrier", 64);
            String t = tracking == null || tracking.isBlank() ? null : tracking.trim();
            if (!"PICKUP".equals(method)) {
                if (c == null) throw bad("A carrier is required to ship.");
                if (t != null && !t.matches("[A-Za-z0-9._/ -]{3,128}")) throw bad("The tracking reference has unsupported characters.");
            }
            jdbc.update("UPDATE order_fulfillments SET status='SHIPPED', carrier=?, tracking_reference=?, shipped_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=? AND tenant_id=?", c, t, fulfillmentId, o.getTenantId());
            Map<Long, OrderItem> byId = new HashMap<>();
            o.getItems().forEach(i -> byId.put(i.getId(), i));
            for (Map<String, Object> li : support.rows("SELECT order_item_id, quantity FROM order_fulfillment_items WHERE tenant_id=? AND fulfillment_id=?", o.getTenantId(), fulfillmentId)) {
                OrderItem item = byId.get(((Number) li.get("orderItemId")).longValue());
                item.setFulfilledQuantity(item.getFulfilledQuantity() + ((Number) li.get("quantity")).intValue());
            }
            refreshOrderFulfillmentStatus(o);
            if (c != null) o.setCarrier(c);
            if (t != null) o.setTrackingNumber(t);
            boolean complete = "FULFILLED".equals(o.getFulfillmentStatus());
            String target = complete ? ("PICKUP".equals(method) ? "FULFILLED" : "SHIPPED") : "PARTIALLY_FULFILLED";
            if (!o.getStatus().equals(target) && OrderStateMachine.canMove(o.getOrderType(), o.getStatus(), target)) {
                admin.apply(actor, o, target, "SHIPMENT", "fulfillment " + fulfillmentId + (t == null ? "" : " tracking " + t), "ADMIN", correlationId, null);
            } else orders.save(o);
            return Map.of("fulfillmentId", fulfillmentId, "orderStatus", o.getStatus(), "fulfillmentStatus", o.getFulfillmentStatus());
        });
        String orderStatus = (String) result.get("orderStatus");
        if (Set.of("SHIPPED", "FULFILLED").contains(orderStatus) && !"PICKUP".equals(f0.get("method"))) {
            tryTriggerDelivery(orderId);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> cancel(AdminPrincipal actor, long fulfillmentId) {
        require(actor, "order:process");
        Map<String, Object> f = fulfillment(actor, fulfillmentId);
        Order o = admin.load(actor, ((Number) f.get("orderId")).longValue(), true);
        f = fulfillment(actor, fulfillmentId);
        if (!Set.of("ALLOCATED", "PACKED").contains(f.get("status"))) throw conflict("Only an unshipped fulfillment can be cancelled (this one is " + f.get("status") + ").");
        Map<Long, OrderItem> byId = new HashMap<>();
        o.getItems().forEach(i -> byId.put(i.getId(), i));
        for (Map<String, Object> li : support.rows("SELECT order_item_id, quantity FROM order_fulfillment_items WHERE tenant_id=? AND fulfillment_id=?", o.getTenantId(), fulfillmentId)) {
            OrderItem item = byId.get(((Number) li.get("orderItemId")).longValue());
            item.setAllocatedQuantity(Math.max(0, item.getAllocatedQuantity() - ((Number) li.get("quantity")).intValue()));
        }
        jdbc.update("UPDATE order_fulfillments SET status='CANCELLED', updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE id=? AND tenant_id=?", fulfillmentId, o.getTenantId());
        refreshOrderFulfillmentStatus(o);
        orders.save(o);
        jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, actor_id, actor_email, source, reason, correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                o.getTenantId(), o.getId(), o.getStatus(), o.getStatus(), "FULFILLMENT_CANCELLED", actor.userId(), actor.email(), "ADMIN", "fulfillment " + fulfillmentId, UUID.randomUUID().toString());
        return Map.of("fulfillmentId", fulfillmentId, "status", "CANCELLED", "fulfillmentStatus", o.getFulfillmentStatus());
    }

    @SuppressWarnings("unchecked")
    private void tryTriggerDelivery(long orderId) {
        try {
            Order o = orders.findById(orderId).orElse(null);
            if (o == null) { log.warn("Delivery auto-create skipped: order {} not found", orderId); return; }
            Map<String, Object> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("tenantId", o.getTenantId().toString());
            body.put("businessType", o.getOrderType());
            body.put("customerName", o.getCustomerName());
            body.put("deliveryAddress", o.getDeliveryAddress());
            body.put("totalAmount", o.getTotalAmount());
            body.put("userId", o.getUserId());
            body.put("itemCount", o.getItems().stream().mapToInt(OrderQueryService::quantity).sum());
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Token", serviceToServiceToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(deliveryServiceUrl + "/internal/delivery/auto-create", new HttpEntity<>(body, headers), Map.class);
            log.info("Delivery auto-create triggered for order {}", orderId);
        } catch (Exception e) {
            log.warn("Delivery auto-create failed for order {} — {}", orderId, e.getMessage());
        }
    }

    private Map<String, Object> fulfillment(AdminPrincipal actor, long id) {
        Map<String, Object> f = support.one("SELECT * FROM order_fulfillments WHERE id=? AND tenant_id=?", "Fulfillment", id, actor.tenantId());
        admin.load(actor, ((Number) f.get("orderId")).longValue(), false); // store authorisation
        return f;
    }

    static void refreshOrderFulfillmentStatus(Order o) {
        int ordered = 0, allocated = 0, fulfilled = 0;
        for (OrderItem i : o.getItems()) { ordered += OrderQueryService.quantity(i); allocated += i.getAllocatedQuantity(); fulfilled += i.getFulfilledQuantity(); }
        o.setFulfillmentStatus(ordered > 0 && fulfilled >= ordered ? "FULFILLED" : fulfilled > 0 ? "PARTIAL" : allocated > 0 ? "ALLOCATED" : "UNFULFILLED");
    }
}

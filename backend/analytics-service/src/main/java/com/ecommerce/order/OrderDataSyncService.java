package com.ecommerce.order;

import com.ecommerce.order.model.CustomerCart;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderReturn;
import com.ecommerce.order.repository.CustomerCartRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderReturnRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderDataSyncService {
    private final OrderRepository orderRepository;
    private final CustomerCartRepository cartRepository;
    private final OrderReturnRepository returnRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.order.url:http://localhost:8083}")
    private String orderServiceUrl;
    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    @org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
    public void initialSync() {
        syncSnapshot();
    }

    @Scheduled(fixedDelayString = "${services.order.snapshot-sync-interval-ms:30000}", initialDelay = 30000)
    public void scheduledSync() {
        syncSnapshot();
    }

    @Transactional
    public synchronized void syncSnapshot() {
        try {
            JsonNode snapshot = restTemplate.exchange(orderServiceUrl + "/internal/order-data",
                    HttpMethod.GET, request(), JsonNode.class).getBody();
            if (snapshot == null || !snapshot.has("orders")) {
                log.warn("Order service returned an empty snapshot; local projection was left unchanged");
                return;
            }

            List<Order> orders = mapOrders(snapshot.path("orders"));
            for (Order order : orders) upsertOrder(order);
            List<Long> orderIds = orders.stream().map(Order::getId).filter(Objects::nonNull).toList();
            List<Order> savedOrders = orderRepository.findAllById(orderIds);
            Map<Long, Order> byId = new HashMap<>();
            for (Order order : savedOrders) byId.put(order.getId(), order);

            if (snapshot.has("carts")) {
                for (CustomerCart cart : mapCarts(snapshot.path("carts"))) upsertCart(cart);
            }
            if (snapshot.has("returns")) {
                List<OrderReturn> returns = mapReturns(snapshot.path("returns"));
                for (OrderReturn item : returns) {
                    Long orderId = item.getOrderId();
                    if (orderId != null) item.setOrder(byId.get(orderId));
                }
                for (OrderReturn item : returns) upsertReturn(item);
            }
            resetSequences();
            log.info("Synchronized analytics read model: {} orders", savedOrders.size());
        } catch (Exception ex) {
            log.warn("Order snapshot synchronization failed; keeping the last analytics read model: {}", ex.getMessage());
        }
    }

    private HttpEntity<Void> request() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", serviceToken);
        return new HttpEntity<>(headers);
    }

    private List<Order> mapOrders(JsonNode rows) {
        List<Order> orders = new ArrayList<>();
        rows.forEach(row -> orders.add(mapOrder(row)));
        return orders;
    }

    private Order mapOrder(JsonNode row) {
        Order order = new Order();
        order.setId(longValue(row, "id"));
        order.setUserId(longValue(row, "userId"));
        order.setStatus(text(row, "status"));
        order.setTotalAmount(decimal(row, "totalAmount"));
        order.setDiscountAmount(decimal(row, "discountAmount"));
        order.setCouponCode(text(row, "couponCode"));
        order.setCarrier(text(row, "carrier"));
        order.setTrackingNumber(text(row, "trackingNumber"));
        order.setEstimatedDelivery(localDateTime(row, "estimatedDelivery"));
        order.setShippingStatus(text(row, "shippingStatus"));
        order.setCreatedAt(localDateTime(row, "createdAt"));
        for (JsonNode rowItem : row.path("items")) {
            OrderItem item = new OrderItem();
            item.setId(longValue(rowItem, "id"));
            item.setProductId(longValue(rowItem, "productId"));
            String productName = text(rowItem, "productName");
            item.setProductName(productName != null ? productName : "Product #" + rowItem.path("productId").asText("unknown"));
            item.setPrice(decimal(rowItem, "price"));
            item.setQuantity(integer(rowItem, "quantity"));
            item.setOrder(order);
            order.addItem(item);
        }
        return order;
    }

    private List<CustomerCart> mapCarts(JsonNode rows) {
        List<CustomerCart> carts = new ArrayList<>();
        rows.forEach(row -> {
            CustomerCart cart = new CustomerCart();
            cart.setId(longValue(row, "id"));
            cart.setUserId(longValue(row, "userId"));
            cart.setItemsJson(text(row, "itemsJson"));
            cart.setStatus(text(row, "status"));
            cart.setCreatedAt(offsetDateTime(row, "createdAt"));
            cart.setUpdatedAt(offsetDateTime(row, "updatedAt"));
            carts.add(cart);
        });
        return carts;
    }

    private List<OrderReturn> mapReturns(JsonNode rows) {
        List<OrderReturn> returns = new ArrayList<>();
        rows.forEach(row -> {
            OrderReturn item = new OrderReturn();
            item.setId(longValue(row, "id"));
            item.setOrderId(longValue(row, "orderId"));
            item.setUserId(longValue(row, "userId"));
            item.setReason(text(row, "reason"));
            item.setStatus(text(row, "status"));
            item.setRefundAmount(decimal(row, "refundAmount"));
            item.setResolutionNotes(text(row, "resolutionNotes"));
            item.setCreatedAt(offsetDateTime(row, "createdAt"));
            item.setResolvedAt(offsetDateTime(row, "resolvedAt"));
            returns.add(item);
        });
        return returns;
    }

    private String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long longValue(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private Integer integer(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private java.math.BigDecimal decimal(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private java.time.LocalDateTime localDateTime(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? null : java.time.LocalDateTime.parse(value);
    }

    private java.time.OffsetDateTime offsetDateTime(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? null : java.time.OffsetDateTime.parse(value);
    }

    private void upsertOrder(Order order) {
        jdbcTemplate.update("""
                INSERT INTO orders (id,user_id,status,total_amount,created_at,discount_amount,coupon_code,carrier,tracking_number,estimated_delivery,shipping_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id,status=EXCLUDED.status,total_amount=EXCLUDED.total_amount,
                  created_at=EXCLUDED.created_at,discount_amount=EXCLUDED.discount_amount,coupon_code=EXCLUDED.coupon_code,
                  carrier=EXCLUDED.carrier,tracking_number=EXCLUDED.tracking_number,estimated_delivery=EXCLUDED.estimated_delivery,
                  shipping_status=EXCLUDED.shipping_status
                """, order.getId(), order.getUserId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt(),
                order.getDiscountAmount(), order.getCouponCode(), order.getCarrier(), order.getTrackingNumber(),
                order.getEstimatedDelivery(), order.getShippingStatus());
        for (OrderItem item : order.getItems()) {
            jdbcTemplate.update("""
                    INSERT INTO order_items (id,product_id,product_name,price,quantity,order_id) VALUES (?,?,?,?,?,?)
                    ON CONFLICT (id) DO UPDATE SET product_id=EXCLUDED.product_id,product_name=EXCLUDED.product_name,
                      price=EXCLUDED.price,quantity=EXCLUDED.quantity,order_id=EXCLUDED.order_id
                    """, item.getId(), item.getProductId(), item.getProductName(), item.getPrice(), item.getQuantity(), order.getId());
        }
    }

    private void upsertCart(CustomerCart cart) {
        jdbcTemplate.update("""
                INSERT INTO customer_cart (id,user_id,items_json,status,created_at,updated_at) VALUES (?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET user_id=EXCLUDED.user_id,items_json=EXCLUDED.items_json,status=EXCLUDED.status,
                  created_at=EXCLUDED.created_at,updated_at=EXCLUDED.updated_at
                """, cart.getId(), cart.getUserId(), cart.getItemsJson(), cart.getStatus(), cart.getCreatedAt(), cart.getUpdatedAt());
    }

    private void upsertReturn(OrderReturn item) {
        jdbcTemplate.update("""
                INSERT INTO order_return (id,order_id,user_id,reason,status,refund_amount,resolution_notes,created_at,resolved_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (id) DO UPDATE SET order_id=EXCLUDED.order_id,user_id=EXCLUDED.user_id,reason=EXCLUDED.reason,
                  status=EXCLUDED.status,refund_amount=EXCLUDED.refund_amount,resolution_notes=EXCLUDED.resolution_notes,
                  created_at=EXCLUDED.created_at,resolved_at=EXCLUDED.resolved_at
                """, item.getId(), item.getOrderId(), item.getUserId(), item.getReason(), item.getStatus(), item.getRefundAmount(),
                item.getResolutionNotes(), item.getCreatedAt(), item.getResolvedAt());
    }

    private void resetSequences() {
        for (String table : List.of("orders", "order_items", "customer_cart", "order_return")) {
            jdbcTemplate.queryForObject("SELECT setval(pg_get_serial_sequence(?, 'id'), COALESCE((SELECT MAX(id) FROM " + table + "), 1), true)",
                    Long.class, table);
        }
    }
}

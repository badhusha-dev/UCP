package com.ecommerce.order.service;

import com.ecommerce.order.dto.CreateReturnRequest;
import com.ecommerce.order.dto.OrderTrackingDto;
import com.ecommerce.order.dto.ProcessReturnRequest;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderReturn;
import com.ecommerce.order.model.OrderReturnItem;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderTrackingEvent;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderReturnRepository;
import com.ecommerce.order.repository.OrderReturnItemRepository;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderTrackingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentAndReturnsService {

    private final OrderRepository orderRepository;
    private final OrderTrackingEventRepository trackingRepository;
    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository returnItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderProducerService orderProducerService;
    private final ReturnStockClient returnStockClient;
    private final com.ecommerce.order.admin.OrderAdminService orderAdminService;

    /** Default true: a return refund stays REFUND_PENDING until the Payment/Accounting side confirms it. Set false only for deployments with no payment integration. */
    @org.springframework.beans.factory.annotation.Value("${orders.returns.require-refund-confirmation:true}")
    private boolean requireRefundConfirmation;

    public OrderTrackingDto getOrderTracking(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + orderId));

        List<OrderTrackingEvent> events = trackingRepository.findByOrderIdOrderByEventTimestampAsc(orderId);
        
        // If no events exist yet, generate initial PLACED event
        if (events.isEmpty()) {
            OrderTrackingEvent initialEvent = new OrderTrackingEvent();
            initialEvent.setOrder(order);
            initialEvent.setStatus("PLACED");
            initialEvent.setLocation("Order Processing Center");
            initialEvent.setDescription("Order received and verified by system.");
            initialEvent.setEventTimestamp(OffsetDateTime.now());
            events = new ArrayList<>();
            events.add(trackingRepository.save(initialEvent));
        }

        return OrderTrackingDto.builder()
                .orderId(order.getId())
                .carrier(order.getCarrier() != null ? order.getCarrier() : "Standard Shipping")
                .trackingNumber(order.getTrackingNumber() != null ? order.getTrackingNumber() : "TRK-" + order.getId() + "9872")
                .shippingStatus(order.getShippingStatus() != null ? order.getShippingStatus() : "PROCESSING")
                .estimatedDelivery(order.getEstimatedDelivery() != null ? order.getEstimatedDelivery() : LocalDateTime.now().plusDays(3))
                .events(events)
                .build();
    }

    private static final java.util.Map<String, String> DELIVERY_TO_SHIPPING = java.util.Map.of(
            "PICKED_UP", "SHIPPED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERED",
            "DELIVERY_FAILED", "DELIVERY_FAILED", "RETURNED", "RETURNED");
    private static final java.util.Map<String, Integer> FORWARD_RANK = java.util.Map.of("SHIPPED", 1, "OUT_FOR_DELIVERY", 2);

    /**
     * Applies a delivery progress report from Delivery Service. Order Service stays authoritative: reports are idempotent,
     * never move an order backwards, and never touch cancelled or already finished orders. Returns whether the order changed.
     */
    @Transactional
    public boolean applyDeliveryStatus(Long orderId, String deliveryStatus) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + orderId));
        String target = DELIVERY_TO_SHIPPING.get(deliveryStatus);
        if (target == null) return false;
        String statusBefore = order.getStatus();
        String shipping = order.getShippingStatus();
        if ("CANCELLED".equals(order.getStatus()) || "DELIVERED".equals(order.getStatus())
                || "DELIVERED".equals(shipping) || "RETURNED".equals(shipping)) return false;
        if (target.equals(shipping)) return false;
        Integer current = FORWARD_RANK.get(shipping);
        Integer next = FORWARD_RANK.get(target);
        if (current != null && next != null && next <= current) return false;
        addTrackingCheckpoint(orderId, target, null, "Updated by Delivery Service: " + deliveryStatus);
        orderAdminService.recordDelivery(orderId, statusBefore, deliveryStatus);
        if ("DELIVERED".equals(target)) {
            orderProducerService.publishOrderEvent(new com.ecommerce.order.dto.OrderEvent(
                    "ORDER_STATUS_UPDATED", order.getId(), order.getUserId(), "DELIVERED", order.getTotalAmount()));
        }
        return true;
    }

    @Transactional
    public OrderTrackingEvent addTrackingCheckpoint(Long orderId, String status, String location, String description) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + orderId));

        OrderTrackingEvent event = new OrderTrackingEvent();
        event.setOrder(order);
        event.setStatus(status);
        event.setLocation(location);
        event.setDescription(description);
        event.setEventTimestamp(OffsetDateTime.now());

        order.setShippingStatus(status);
        if ("DELIVERED".equalsIgnoreCase(status)) {
            order.setStatus("DELIVERED");
        }
        orderRepository.save(order);

        return trackingRepository.save(event);
    }

    public List<OrderReturn> getUserReturns(Long userId) {
        return returnRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private BigDecimal safe(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }

}

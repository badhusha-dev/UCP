package com.ecommerce.order.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
public class OrderEvent {
    private String eventType; // ORDER_CREATED, ORDER_CANCELLED, ORDER_STATUS_UPDATED
    private Long orderId;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private String reason;
    private String referenceId;
    /** The order's tenant. OrderProducerService fills it from the order when a caller leaves it empty. */
    private UUID tenantId;

    public OrderEvent(String eventType, Long orderId, Long userId, String status, BigDecimal totalAmount) {
        this(eventType, orderId, userId, status, totalAmount, null, null);
    }

    public OrderEvent(String eventType, Long orderId, Long userId, String status, BigDecimal totalAmount,
                      String reason, String referenceId) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.reason = reason;
        this.referenceId = referenceId;
    }
}

package com.ecommerce.notification.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderEvent {
    private String eventType;
    private Long orderId;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private String reason;
    private String referenceId;
}

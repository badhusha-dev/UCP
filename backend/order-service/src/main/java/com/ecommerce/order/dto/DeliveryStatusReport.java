package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Delivery Service's report of delivery progress. Order Service decides whether and how it changes the order. */
@Data
public class DeliveryStatusReport {
    private String eventId;
    private String deliveryId;
    @NotBlank
    @Pattern(regexp = "PICKED_UP|IN_TRANSIT|DELIVERED|DELIVERY_FAILED|RETURNED|CANCELLED")
    private String deliveryStatus;
}

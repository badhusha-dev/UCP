package com.ecommerce.order.dto;

import com.ecommerce.order.model.OrderTrackingEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Shipment tracking timeline and carrier details")
public class OrderTrackingDto {

    @Schema(description = "Order ID", example = "501")
    private Long orderId;

    @Schema(description = "Shipping Carrier", example = "FedEx")
    private String carrier;

    @Schema(description = "Tracking tracking number", example = "TRK-98234123")
    private String trackingNumber;

    @Schema(description = "Current shipping status", example = "OUT_FOR_DELIVERY")
    private String shippingStatus;

    @Schema(description = "Estimated delivery date", example = "2026-10-28T18:00:00")
    private LocalDateTime estimatedDelivery;

    @Schema(description = "Ordered list of tracking milestone events")
    private List<OrderTrackingEvent> events;
}

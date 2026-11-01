package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Payload for transitioning order lifecycle status")
public class OrderStatusUpdateRequest {
    @Schema(description = "Target order status", example = "CONFIRMED", allowableValues = {"PENDING", "CONFIRMED", "PENDING_INVENTORY", "CANCELLED"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}

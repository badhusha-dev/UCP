package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private Long orderId;

    /** Same key from the same tenant returns the original reservation (safe retries, duplicate order events). */
    private String idempotencyKey;

    /** When true, reserve what is available and record the shortfall as backorder instead of failing. */
    private Boolean allowPartial;

    @Builder.Default
    private Integer holdDurationMinutes = 15;
}

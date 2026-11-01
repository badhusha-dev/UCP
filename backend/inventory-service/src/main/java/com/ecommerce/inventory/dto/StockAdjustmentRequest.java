package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Adjustment delta is required")
    private Integer delta; // Positive to add stock, negative to deduct

    @NotNull(message = "Movement type is required")
    private String movementType; // ADJUSTMENT, RECEIVE, RETURN_RESTOCK, DAMAGE_WRITE_OFF

    private String reason;
}

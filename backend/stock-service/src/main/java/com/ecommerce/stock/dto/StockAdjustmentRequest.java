package com.ecommerce.stock.dto;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonAlias("quantityDelta")
    private Integer delta; // Positive to add stock, negative to deduct

    private String movementType; // ADJUSTMENT, RECEIVE, RETURN_RESTOCK, DAMAGE_WRITE_OFF

    private String reason;
}

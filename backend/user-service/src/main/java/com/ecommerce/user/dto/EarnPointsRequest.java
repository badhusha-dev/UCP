package com.ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request to accrue reward points from an order")
public class EarnPointsRequest {

    @NotNull(message = "Order amount is required")
    @Schema(description = "Completed order dollar amount", example = "125.00")
    private BigDecimal orderAmount;

    @Schema(description = "Source order ID or reference", example = "501")
    private Long orderId;
}

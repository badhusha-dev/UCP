package com.ecommerce.order.accounting.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRefundRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @DecimalMin(value = "0.01", message = "Refund amount must be positive")
    private BigDecimal amount; // Optional: null means full order amount

    @NotBlank(message = "Refund reason is mandatory")
    private String reason;
}

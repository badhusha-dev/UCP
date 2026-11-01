package com.ecommerce.stock.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertSubscriptionRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Email is required")
    @Email(message = "Valid email is required")
    private String email;

    private Long userId;
}

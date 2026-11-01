package com.ecommerce.cart.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartFunnelDto {
    private long totalCartsCreated;
    private long activeCarts;
    private long abandonedCarts;
    private long convertedCarts;
    private BigDecimal abandonedCartValue;
    private BigDecimal activeCartValue;
    private double abandonedRatePercent;
    private double conversionRatePercent;
    private long remindersSent;
}

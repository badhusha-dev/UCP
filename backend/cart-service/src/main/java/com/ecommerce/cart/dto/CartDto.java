package com.ecommerce.cart.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartDto {
    private Long id;
    private Long userId;
    private String sessionToken;
    private String status;
    private String currency;
    private int totalQuantity;
    private BigDecimal subtotal;
    private List<CartItemDto> items;
    private OffsetDateTime updatedAt;
    private OffsetDateTime createdAt;
}

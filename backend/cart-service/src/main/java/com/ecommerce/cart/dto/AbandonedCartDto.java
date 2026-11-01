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
public class AbandonedCartDto {
    private Long cartId;
    private Long userId;
    private String customerEmail;
    private String sessionToken;
    private int itemCount;
    private BigDecimal totalAmount;
    private OffsetDateTime lastUpdated;
    private long inactiveHours;
    private int remindersSent;
    private String status;
    private List<CartItemDto> items;
}

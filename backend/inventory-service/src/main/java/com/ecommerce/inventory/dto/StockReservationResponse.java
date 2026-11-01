package com.ecommerce.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationResponse {
    private String reservationId;
    private Long productId;
    private Integer quantity;
    private String status;
    private OffsetDateTime expiresAt;
    private String message;
}

package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed order representation for admin dashboard table and drill-down inspection")
public class AdminOrderRowDto {
    private Long id;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private String couponCode;
    private String carrier;
    private String trackingNumber;
    private String shippingStatus;
    private LocalDateTime createdAt;
    private Integer itemCount;
    private List<OrderItemSummaryDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemSummaryDto {
        private Long productId;
        private String productName;
        private BigDecimal price;
        private Integer quantity;
    }
}

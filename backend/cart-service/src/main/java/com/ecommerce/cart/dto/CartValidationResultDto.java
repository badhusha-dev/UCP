package com.ecommerce.cart.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartValidationResultDto {
    private boolean valid;
    private int totalQuantity;
    private BigDecimal subtotal;
    private String message;
    private List<ItemValidation> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemValidation {
        private Long productId;
        private String sku;
        private String productName;
        private Integer requestedQuantity;
        private Integer availableStock;
        private BigDecimal unitPrice;
        private boolean inStock;
        private String statusMessage;
    }
}

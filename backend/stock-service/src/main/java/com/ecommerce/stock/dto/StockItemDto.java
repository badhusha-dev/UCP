package com.ecommerce.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockItemDto {
    private Long id;
    private Long productId;
    private String sku;
    private Integer stockQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Integer reorderThreshold;
    private BigDecimal costPrice;
    private String warehouseLocation;
    private boolean lowStock;
    private OffsetDateTime updatedAt;
}

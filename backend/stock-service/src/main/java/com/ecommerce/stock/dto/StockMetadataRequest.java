package com.ecommerce.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockMetadataRequest {
    @NotNull
    private Long productId;
    private String sku;
    @Min(0)
    private Integer reorderThreshold;
    @Min(0)
    private BigDecimal costPrice;
    private String warehouseLocation;
}

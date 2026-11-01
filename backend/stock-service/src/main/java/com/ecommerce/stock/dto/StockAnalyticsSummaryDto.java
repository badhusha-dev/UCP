package com.ecommerce.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalyticsSummaryDto {
    private long totalProductsTracked;
    private long totalPhysicalUnits;
    private long lowStockCount;
    private long outOfStockCount;
    private BigDecimal stockValuation;
    private List<StockItemDto> lowStockAlerts;
}

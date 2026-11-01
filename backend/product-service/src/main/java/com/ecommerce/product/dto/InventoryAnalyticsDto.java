package com.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Inventory intelligence and warehouse valuation analytics")
public class InventoryAnalyticsDto {
    @Schema(description = "Total unique product catalog items", example = "42")
    private Long totalProducts;

    @Schema(description = "Aggregate physical units available across all products", example = "1250")
    private Long totalUnitsInStock;

    @Schema(description = "Number of items currently at or below their reorder threshold", example = "3")
    private Long lowStockCount;

    @Schema(description = "Number of items with zero units remaining", example = "1")
    private Long outOfStockCount;

    @Schema(description = "Total inventory asset valuation based on wholesale cost price", example = "58420.00")
    private BigDecimal stockValuation;

    @Schema(description = "Percentage of active catalog with verified cost price data", example = "100.0")
    private Double costCoveragePercent;

    @Schema(description = "List of products that have breached their reorder threshold")
    private List<LowStockItemDto> lowStockItems;

    @Schema(description = "Inventory distribution summarized by category")
    private List<CategoryInventorySummaryDto> categoryBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LowStockItemDto {
        private Long id;
        private String name;
        private Integer stockQuantity;
        private Integer reorderThreshold;
        private Double price;
        private BigDecimal costPrice;
        private String categoryName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInventorySummaryDto {
        private Long categoryId;
        private String categoryName;
        private Long productCount;
        private Long totalUnits;
        private BigDecimal valuation;
    }
}

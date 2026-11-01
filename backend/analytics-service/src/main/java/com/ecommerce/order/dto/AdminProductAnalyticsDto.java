package com.ecommerce.order.dto;

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
@Schema(description = "Product performance rankings and category sales distribution")
public class AdminProductAnalyticsDto {

    @Schema(description = "Top selling products sorted by gross sales revenue")
    private List<ProductPerformanceDto> topProductsByRevenue;

    @Schema(description = "Top selling products sorted by units sold")
    private List<ProductPerformanceDto> topProductsByUnits;

    @Schema(description = "Slow moving products with low order velocity")
    private List<ProductPerformanceDto> slowMovingProducts;

    @Schema(description = "Sales and order volume aggregated by product category")
    private List<CategoryPerformanceDto> categoryPerformance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductPerformanceDto {
        private Long productId;
        private String productName;
        private Long unitsSold;
        private BigDecimal totalRevenue;
        private Long orderCount;
        private Double averageSellingPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryPerformanceDto {
        private String category;
        private Long unitsSold;
        private BigDecimal totalRevenue;
        private Long orderCount;
        private Double revenuePercentage;
    }
}

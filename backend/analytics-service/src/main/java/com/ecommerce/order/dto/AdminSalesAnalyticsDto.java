package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Daily sales trends, status breakdown, and period-over-period series")
public class AdminSalesAnalyticsDto {

    @Schema(description = "Chronological daily points for sales and order count")
    private List<DailySalesPointDto> dailySeries;

    @Schema(description = "Count of orders distributed by lifecycle status")
    private Map<String, Long> statusBreakdown;

    @Schema(description = "Monetary volume of orders distributed by lifecycle status")
    private Map<String, BigDecimal> salesByStatus;

    @Schema(description = "Total GMV across the queried period")
    private BigDecimal periodTotalGmv;

    @Schema(description = "Total Net Sales across the queried period")
    private BigDecimal periodTotalNetSales;

    @Schema(description = "Total Orders placed in the queried period")
    private Long periodTotalOrders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySalesPointDto {
        @Schema(description = "Calendar date (YYYY-MM-DD)", example = "2026-10-20")
        private String date;

        @Schema(description = "Gross Merchandise Value on this date", example = "4250.00")
        private BigDecimal gmv;

        @Schema(description = "Net Sales on this date", example = "3980.00")
        private BigDecimal netSales;

        @Schema(description = "Total orders placed on this date", example = "24")
        private Long orderCount;

        @Schema(description = "Physical items purchased on this date", example = "58")
        private Long unitsSold;

        @Schema(description = "Net Sales on the corresponding date in the prior comparison period", example = "3500.00")
        private BigDecimal comparisonSales;

        @Schema(description = "Order count on the corresponding date in the prior comparison period", example = "20")
        private Long comparisonOrderCount;
    }
}

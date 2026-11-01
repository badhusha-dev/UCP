package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Executive Overview Business Analytics DTO with Period-over-Period comparisons")
public class AdminOverviewAnalyticsDto {

    @Schema(description = "Configured time range window", example = "LAST_30_DAYS")
    private String timeRange;

    @Schema(description = "Start boundary instant (UTC)", example = "2026-09-23T00:00:00")
    private LocalDateTime startDate;

    @Schema(description = "End boundary instant (UTC)", example = "2026-10-23T23:59:59")
    private LocalDateTime endDate;

    @Schema(description = "Standard timezone applied for time boundaries", example = "UTC")
    @Builder.Default
    private String timezone = "UTC";

    @Schema(description = "Top-line Gross Merchandise Value prior to discounts and refunds")
    private MetricCardDto<BigDecimal> grossMerchandiseValue;

    @Schema(description = "Realized Net Sales after discounts and processed returns")
    private MetricCardDto<BigDecimal> netSales;

    @Schema(description = "Total commercial transactions placed")
    private MetricCardDto<Long> ordersPlaced;

    @Schema(description = "Orders with fulfilled/delivered status")
    private MetricCardDto<Long> deliveredOrders;

    @Schema(description = "Average order transaction value")
    private MetricCardDto<BigDecimal> averageOrderValue;

    @Schema(description = "Orders aborted before delivery")
    private MetricCardDto<Long> cancelledOrders;

    @Schema(description = "Percentage of placed orders cancelled")
    private MetricCardDto<Double> cancellationRate;

    @Schema(description = "Total refund dollars processed")
    private MetricCardDto<BigDecimal> refundAmount;

    @Schema(description = "Ratio of refunds against gross sales")
    private MetricCardDto<Double> refundRate;

    @Schema(description = "Total physical line-item quantities sold")
    private MetricCardDto<Long> unitsSold;

    @Schema(description = "First-time purchasing customers in this window")
    private MetricCardDto<Long> newCustomers;

    @Schema(description = "Repeat customers with previous purchase history")
    private MetricCardDto<Long> returningCustomers;

    @Schema(description = "Current items at or below reorder threshold", example = "3")
    private Long lowStockCount;

    @Schema(description = "Active persistent customer carts in database", example = "14")
    private Long activeCartsCount;

    @Schema(description = "Commercial accuracy and GAAP disclaimer")
    @Builder.Default
    private String disclaimer = "Calculated from primary database transaction ledger. Gross profit is withheld until wholesale product cost coverage is verified.";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricCardDto<T> {
        @Schema(description = "Current period value")
        private T current;

        @Schema(description = "Previous equivalent comparison period value")
        private T previous;

        @Schema(description = "Percentage change vs comparison period", example = "+14.2")
        private Double deltaPercent;

        @Schema(description = "Trend direction indicator", example = "UP", allowableValues = {"UP", "DOWN", "FLAT"})
        private String trend;
    }
}

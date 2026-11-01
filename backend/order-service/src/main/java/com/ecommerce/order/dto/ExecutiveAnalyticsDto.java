package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Executive commerce analytics including revenue, AOV, returns, and conversion metrics")
public class ExecutiveAnalyticsDto {

    @Schema(description = "Gross Merchandise Value (GMV) across all recorded orders", example = "15420.50")
    private BigDecimal grossMerchandiseValue;

    @Schema(description = "Net recognized revenue after deducting discounts and customer refunds", example = "13890.25")
    private BigDecimal netRevenue;

    @Schema(description = "Total promotional discounts granted across all orders", example = "840.00")
    private BigDecimal totalDiscounts;

    @Schema(description = "Total processed customer refunds", example = "690.25")
    private BigDecimal totalRefunds;

    @Schema(description = "Average Order Value (AOV)", example = "128.50")
    private BigDecimal averageOrderValue;

    @Schema(description = "Total lifetime order count", example = "120")
    private Long totalOrders;

    @Schema(description = "Total confirmed/fulfilled order count", example = "108")
    private Long completedOrders;

    @Schema(description = "Orders in pending or processing state", example = "8")
    private Long pendingOrders;

    @Schema(description = "Cancelled or returned orders count", example = "4")
    private Long cancelledOrders;

    @Schema(description = "Estimated checkout conversion rate (percentage)", example = "3.45")
    private Double conversionRate;

    @Schema(description = "Return rate percentage (returns / total orders)", example = "3.33")
    private Double returnRate;

    @Schema(description = "Breakdown of order counts by status")
    private Map<String, Long> statusBreakdown;

    @Schema(description = "Executive disclaimer stating platform values represent internal transactional summaries",
            example = "Platform transactional totals only. Not an audited GAAP financial statement.")
    private String platformDisclaimer;
}

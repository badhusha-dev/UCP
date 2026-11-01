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
@Schema(description = "Customer retention, repeat purchasing patterns, and high-value customer ranking")
public class AdminCustomerAnalyticsDto {

    @Schema(description = "Total distinct customers who placed orders in this window", example = "120")
    private Long totalUniqueCustomers;

    @Schema(description = "First-time purchasers in platform history", example = "45")
    private Long newCustomers;

    @Schema(description = "Customers with prior platform orders", example = "75")
    private Long returningCustomers;

    @Schema(description = "Percentage of customers with 2 or more lifetime orders", example = "62.5")
    private Double repeatPurchaseRate;

    @Schema(description = "Average number of orders placed per active customer", example = "1.8")
    private Double averageOrdersPerCustomer;

    @Schema(description = "Top spending customers during the queried period")
    private List<CustomerSpendingDto> topCustomers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerSpendingDto {
        private Long userId;
        private Long orderCount;
        private BigDecimal totalSpent;
        private BigDecimal averageOrderValue;
        private LocalDateTime firstOrderDate;
        private LocalDateTime lastOrderDate;
    }
}

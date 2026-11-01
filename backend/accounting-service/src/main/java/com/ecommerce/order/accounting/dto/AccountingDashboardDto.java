package com.ecommerce.order.accounting.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingDashboardDto {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String currency;

    private BigDecimal grossSales;
    private BigDecimal totalRefunds;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal totalAdjustments;
    private BigDecimal netRecordedActivity;

    private long failedOrPendingCount;
    private BigDecimal failedOrPendingAmount;

    private long flaggedReconciliationCount;
    private long totalTransactionsCount;

    private String disclaimer;
}

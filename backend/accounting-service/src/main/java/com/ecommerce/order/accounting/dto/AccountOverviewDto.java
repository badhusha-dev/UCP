package com.ecommerce.order.accounting.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountOverviewDto {
    private Long userId;
    private String currency;

    // Monetary KPI Figures
    private BigDecimal availableStoreCredit;
    private BigDecimal lifetimeSpend;
    private BigDecimal lifetimeRefunds;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal netActivity;

    // Pending Counts & Values
    private long pendingTransactionsCount;
    private BigDecimal pendingTransactionsAmount;

    // Explicit Calculation Explanations
    private String storeCreditExplanation;
    private String lifetimeSpendExplanation;
    private String lifetimeRefundsExplanation;
    private String netActivityExplanation;
    private String pendingExplanation;
}

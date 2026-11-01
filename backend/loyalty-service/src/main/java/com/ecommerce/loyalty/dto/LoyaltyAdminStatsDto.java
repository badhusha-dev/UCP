package com.ecommerce.loyalty.dto;

import java.math.BigDecimal;
import java.util.Map;

public class LoyaltyAdminStatsDto {
    private Long totalAccounts;
    private Map<String, Long> tierBreakdown;
    private Long totalPointsOutstanding;
    private Long totalLifetimePointsIssued;
    private BigDecimal estimatedLiabilityUsd;
    private BigDecimal pointValueUsd;

    // Getters and setters
    public Long getTotalAccounts() { return totalAccounts; }
    public void setTotalAccounts(Long totalAccounts) { this.totalAccounts = totalAccounts; }

    public Map<String, Long> getTierBreakdown() { return tierBreakdown; }
    public void setTierBreakdown(Map<String, Long> tierBreakdown) { this.tierBreakdown = tierBreakdown; }

    public Long getTotalPointsOutstanding() { return totalPointsOutstanding; }
    public void setTotalPointsOutstanding(Long totalPointsOutstanding) { this.totalPointsOutstanding = totalPointsOutstanding; }

    public Long getTotalLifetimePointsIssued() { return totalLifetimePointsIssued; }
    public void setTotalLifetimePointsIssued(Long totalLifetimePointsIssued) { this.totalLifetimePointsIssued = totalLifetimePointsIssued; }

    public BigDecimal getEstimatedLiabilityUsd() { return estimatedLiabilityUsd; }
    public void setEstimatedLiabilityUsd(BigDecimal estimatedLiabilityUsd) { this.estimatedLiabilityUsd = estimatedLiabilityUsd; }

    public BigDecimal getPointValueUsd() { return pointValueUsd; }
    public void setPointValueUsd(BigDecimal pointValueUsd) { this.pointValueUsd = pointValueUsd; }
}

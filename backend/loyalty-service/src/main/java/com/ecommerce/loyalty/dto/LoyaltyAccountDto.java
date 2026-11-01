package com.ecommerce.loyalty.dto;

import com.ecommerce.loyalty.model.LoyaltyTier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class LoyaltyAccountDto {
    private Long id;
    private Long userId;
    private Integer pointsBalance;
    private Integer lifetimePoints;
    private LoyaltyTier tier;
    private BigDecimal cashValue;
    private Integer pointsToNextTier;
    private LoyaltyTier nextTier;
    private BigDecimal tierMultiplier;
    private LocalDateTime updatedAt;
    private List<LoyaltyTransactionDto> recentTransactions;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getPointsBalance() { return pointsBalance; }
    public void setPointsBalance(Integer pointsBalance) { this.pointsBalance = pointsBalance; }

    public Integer getLifetimePoints() { return lifetimePoints; }
    public void setLifetimePoints(Integer lifetimePoints) { this.lifetimePoints = lifetimePoints; }

    public LoyaltyTier getTier() { return tier; }
    public void setTier(LoyaltyTier tier) { this.tier = tier; }

    public BigDecimal getCashValue() { return cashValue; }
    public void setCashValue(BigDecimal cashValue) { this.cashValue = cashValue; }

    public Integer getPointsToNextTier() { return pointsToNextTier; }
    public void setPointsToNextTier(Integer pointsToNextTier) { this.pointsToNextTier = pointsToNextTier; }

    public LoyaltyTier getNextTier() { return nextTier; }
    public void setNextTier(LoyaltyTier nextTier) { this.nextTier = nextTier; }

    public BigDecimal getTierMultiplier() { return tierMultiplier; }
    public void setTierMultiplier(BigDecimal tierMultiplier) { this.tierMultiplier = tierMultiplier; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<LoyaltyTransactionDto> getRecentTransactions() { return recentTransactions; }
    public void setRecentTransactions(List<LoyaltyTransactionDto> recentTransactions) { this.recentTransactions = recentTransactions; }
}

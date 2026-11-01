package com.ecommerce.loyalty.dto;

import com.ecommerce.loyalty.model.LoyaltyTier;
import java.math.BigDecimal;

public class LoyaltyTierRuleDto {
    private LoyaltyTier tier;
    private Integer minLifetimePoints;
    private BigDecimal multiplier;
    private BigDecimal discountPercentBonus;
    private String perksDescription;

    // Getters and setters
    public LoyaltyTier getTier() { return tier; }
    public void setTier(LoyaltyTier tier) { this.tier = tier; }

    public Integer getMinLifetimePoints() { return minLifetimePoints; }
    public void setMinLifetimePoints(Integer minLifetimePoints) { this.minLifetimePoints = minLifetimePoints; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public BigDecimal getDiscountPercentBonus() { return discountPercentBonus; }
    public void setDiscountPercentBonus(BigDecimal discountPercentBonus) { this.discountPercentBonus = discountPercentBonus; }

    public String getPerksDescription() { return perksDescription; }
    public void setPerksDescription(String perksDescription) { this.perksDescription = perksDescription; }
}

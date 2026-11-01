package com.ecommerce.loyalty.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "loyalty_tier_rules")
public class LoyaltyTierRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, unique = true, length = 20)
    private LoyaltyTier tier;

    @Column(name = "min_lifetime_points", nullable = false)
    private Integer minLifetimePoints;

    @Column(name = "multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal multiplier;

    @Column(name = "discount_percent_bonus", precision = 4, scale = 2)
    private BigDecimal discountPercentBonus;

    @Column(name = "perks_description", length = 500)
    private String perksDescription;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

package com.ecommerce.loyalty.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loyalty_accounts")
public class LoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Not unique alone — uniqueness is now (user_id, tenant_id) enforced by DB index
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "points_balance", nullable = false)
    private Integer pointsBalance = 0;

    @Column(name = "lifetime_points", nullable = false)
    private Integer lifetimePoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private LoyaltyTier tier = LoyaltyTier.BRONZE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public LoyaltyAccount() {}

    public LoyaltyAccount(Long userId) {
        this.userId = userId;
        this.pointsBalance = 0;
        this.lifetimePoints = 0;
        this.tier = LoyaltyTier.BRONZE;
    }

    public void recalculateTier() {
        if (lifetimePoints >= 10000) {
            tier = LoyaltyTier.PLATINUM;
        } else if (lifetimePoints >= 5000) {
            tier = LoyaltyTier.GOLD;
        } else if (lifetimePoints >= 1000) {
            tier = LoyaltyTier.SILVER;
        } else {
            tier = LoyaltyTier.BRONZE;
        }
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Integer getPointsBalance() { return pointsBalance; }
    public void setPointsBalance(Integer pointsBalance) { this.pointsBalance = pointsBalance; }

    public Integer getLifetimePoints() { return lifetimePoints; }
    public void setLifetimePoints(Integer lifetimePoints) { this.lifetimePoints = lifetimePoints; }

    public LoyaltyTier getTier() { return tier; }
    public void setTier(LoyaltyTier tier) { this.tier = tier; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.ecommerce.user.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_loyalty")
@Data
@Schema(description = "Customer loyalty account tracking reward points and membership tier")
public class CustomerLoyalty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    @Schema(description = "User unique ID", example = "2")
    private Long userId;

    @Column(name = "points_balance", nullable = false)
    @Schema(description = "Current spendable reward points balance", example = "250")
    private Integer pointsBalance = 0;

    @Column(name = "lifetime_points", nullable = false)
    @Schema(description = "Total points accrued over account lifetime", example = "500")
    private Integer lifetimePoints = 0;

    @Column(nullable = false, length = 32)
    @Schema(description = "Membership tier: BRONZE, SILVER, GOLD, PLATINUM", example = "BRONZE")
    private String tier = "BRONZE";

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public void recalculateTier() {
        if (this.lifetimePoints >= 5000) {
            this.tier = "PLATINUM";
        } else if (this.lifetimePoints >= 2000) {
            this.tier = "GOLD";
        } else if (this.lifetimePoints >= 500) {
            this.tier = "SILVER";
        } else {
            this.tier = "BRONZE";
        }
        this.updatedAt = OffsetDateTime.now();
    }
}

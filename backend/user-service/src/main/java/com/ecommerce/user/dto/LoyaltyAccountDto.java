package com.ecommerce.user.dto;

import com.ecommerce.user.model.LoyaltyTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer loyalty rewards profile with points, tier, and history")
public class LoyaltyAccountDto {

    @Schema(description = "User ID", example = "2")
    private Long userId;

    @Schema(description = "Available reward points", example = "250")
    private Integer pointsBalance;

    @Schema(description = "Lifetime points earned", example = "500")
    private Integer lifetimePoints;

    @Schema(description = "Membership tier: BRONZE, SILVER, GOLD, PLATINUM", example = "BRONZE")
    private String tier;

    @Schema(description = "Cash discount value of available points (e.g. 100 points = $5.00)", example = "12.50")
    private BigDecimal cashValue;

    @Schema(description = "Next tier threshold points needed", example = "250")
    private Integer pointsToNextTier;

    @Schema(description = "Next upcoming tier name", example = "SILVER")
    private String nextTier;

    @Schema(description = "Recent loyalty points transactions")
    private List<LoyaltyTransaction> recentTransactions;
}

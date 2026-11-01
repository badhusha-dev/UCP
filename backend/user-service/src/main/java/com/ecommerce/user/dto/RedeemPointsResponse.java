package com.ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Outcome of points redemption for discount")
public class RedeemPointsResponse {

    @Schema(description = "Whether points redemption succeeded", example = "true")
    private boolean success;

    @Schema(description = "Points deducted", example = "100")
    private Integer pointsRedeemed;

    @Schema(description = "Dollar discount value granted", example = "5.00")
    private BigDecimal discountAmount;

    @Schema(description = "Remaining points balance", example = "150")
    private Integer remainingBalance;

    @Schema(description = "Redemption message", example = "100 points redeemed for a $5.00 discount!")
    private String message;
}

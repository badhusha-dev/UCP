package com.ecommerce.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to redeem reward points for checkout discount")
public class RedeemPointsRequest {

    @NotNull(message = "Points to redeem is required")
    @Min(value = 50, message = "Minimum redemption is 50 points")
    @Schema(description = "Points to redeem (e.g. 100 points = $5.00)", example = "100")
    private Integer points;

    @Schema(description = "Target checkout order ID or reference", example = "501")
    private Long orderId;
}

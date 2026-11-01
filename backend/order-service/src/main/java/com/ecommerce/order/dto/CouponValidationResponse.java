package com.ecommerce.order.dto;

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
@Schema(description = "Coupon validation outcome including calculated discount")
public class CouponValidationResponse {

    @Schema(description = "Whether the coupon is valid and eligible", example = "true")
    private boolean valid;

    @Schema(description = "Coupon code string", example = "WELCOME10")
    private String couponCode;

    @Schema(description = "Discount type: PERCENTAGE or FIXED", example = "PERCENTAGE")
    private String discountType;

    @Schema(description = "Original discount value", example = "10.00")
    private BigDecimal discountValue;

    @Schema(description = "Calculated monetary discount amount deducted", example = "15.00")
    private BigDecimal discountAmount;

    @Schema(description = "Resulting order amount after discount", example = "135.00")
    private BigDecimal finalAmount;

    @Schema(description = "Validation status or explanation message", example = "10% discount applied successfully!")
    private String message;
}

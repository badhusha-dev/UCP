package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Coupon validation request payload")
public class ValidateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    @Schema(description = "Coupon code string", example = "WELCOME10", requiredMode = Schema.RequiredMode.REQUIRED)
    private String couponCode;

    @NotNull(message = "Order amount is required")
    @Schema(description = "Basket subtotal amount before discount", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal orderAmount;
}

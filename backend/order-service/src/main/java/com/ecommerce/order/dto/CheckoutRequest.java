package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "Checkout request payload containing user ID and cart items")
public class CheckoutRequest {
    @Schema(description = "User unique identifier placing the order", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @ArraySchema(schema = @Schema(implementation = CartItemDto.class), arraySchema = @Schema(description = "Items to purchase", requiredMode = Schema.RequiredMode.REQUIRED))
    private List<CartItemDto> items;

    @Schema(description = "Optional promotional coupon code to apply", example = "WELCOME10")
    private String couponCode;

    @Schema(description = "Public slug of the storefront the order is placed in. The order belongs to that storefront's tenant and store; omitted = legacy single-tenant checkout.", example = "acme")
    private String storefront;
    @Schema(description = "Store the order was placed in (defaults to the tenant default store)")
    private String storeId;
    @Schema(description = "Vendor/restaurant fulfilling the order")
    private String vendorId;
    @Schema(description = "RETAIL (default) or RESTAURANT", example = "RETAIL")
    private String orderType;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String deliveryAddress;
    private String specialInstructions;
}

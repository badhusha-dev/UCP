package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Cart item input for order checkout")
public class CartItemDto {
    @Schema(description = "Product unique identifier", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productId;

    @Schema(description = "Product name", example = "Wireless Headphones")
    private String name;

    @Schema(description = "Unit price in USD", example = "199.99", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;

    @Schema(description = "Quantity to order", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

    @Schema(description = "Vendor that supplies this line (multi-vendor orders)")
    private String vendorId;
}

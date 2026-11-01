package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Shopping cart sync payload")
public class CartSyncRequest {

    @NotBlank(message = "itemsJson must not be blank")
    @Schema(description = "JSON string representation of cart items", example = "[{\"productId\":1,\"quantity\":2}]", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemsJson;
}

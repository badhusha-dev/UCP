package com.ecommerce.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ReturnLineRequest {
    @NotNull @Positive
    private Long orderItemId;

    @NotNull @Positive
    private Integer quantity;
}

package com.ecommerce.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StockReturnRequest {
    @NotNull @Positive
    private Long productId;

    @NotNull @Min(1)
    private Integer quantity;

    @NotBlank @Size(max = 150)
    private String referenceId;

    @Size(max = 255)
    private String reason;
}

package com.ecommerce.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockReceiptRequest {
    @NotNull @Positive
    private Long productId;

    @NotNull @Min(1)
    private Integer quantity;

    @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4)
    private BigDecimal unitCost;

    @NotBlank @Size(max = 150)
    private String referenceId;

    @Size(max = 255)
    private String reason;
}

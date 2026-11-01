package com.ecommerce.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMetadataRequest {

    @NotNull
    @Positive
    private Long productId;

    @Size(max = 64)
    private String sku;

    @NotNull
    @Min(0)
    private Integer reorderThreshold;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 34, fraction = 4)
    private BigDecimal costPrice;

    @Size(max = 128)
    private String warehouseLocation;
}

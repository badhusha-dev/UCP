package com.ecommerce.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductAdminRequest {
    @NotBlank
    private String name;
    private String sku;
    private String description;
    @NotNull
    @PositiveOrZero
    private Double price;
    @PositiveOrZero
    private BigDecimal costPrice;
    @PositiveOrZero
    private Integer reorderThreshold;
    private Long categoryId;
    private String imageUrl;
}

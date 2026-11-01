package com.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product variant view DTO")
public class ProductVariantDto {
    private Long id;
    private Long productId;
    private String sku;
    private String name;
    private String color;
    private String size;
    private Double priceAdjustment;
    private Double finalPrice;
    private Integer stockQuantity;
}

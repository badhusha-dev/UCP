package com.ecommerce.product.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_variant")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product variant entity for colors, sizes, and SKUs")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String color;

    @Column(length = 50)
    private String size;

    @Column(name = "price_adjustment")
    private Double priceAdjustment;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;
}

package com.ecommerce.product.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product entity representing catalog items and available inventory")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Unique product identifier", example = "101")
    private Long id;

    public static final java.util.UUID LEGACY_TENANT_ID = java.util.UUID.fromString("00000000-0000-4000-8000-000000000001");

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "tenant_id", nullable = false, updatable = false)
    @Builder.Default
    private java.util.UUID tenantId = LEGACY_TENANT_ID;

    @Schema(description = "Stock keeping unit, unique per client", example = "HP-NC-100")
    @Column(length = 64)
    private String sku;

    @Schema(description = "Product title/name", example = "Wireless Noise Canceling Headphones")
    @Column(nullable = false)
    private String name;

    @Schema(description = "Detailed product description", example = "Over-ear bluetooth headphones with 30-hour battery life")
    private String description;

    @Schema(description = "Unit price in USD", example = "199.99")
    @Column(nullable = false)
    private Double price;

    @Schema(description = "Current stock quantity available in inventory", example = "50")
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Schema(description = "Wholesale cost / unit acquisition cost for COGS and margin calculation", example = "85.00")
    @Column(name = "cost_price")
    private java.math.BigDecimal costPrice;

    @Schema(description = "Low stock alert reorder threshold quantity", example = "10")
    @Column(name = "reorder_threshold")
    @Builder.Default
    private Integer reorderThreshold = 10;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    @Schema(description = "Assigned category")
    private ProductCategory category;

    @Schema(description = "Aggregated average rating (1.0 to 5.0)", example = "4.8")
    @Builder.Default
    private Double rating = 5.0;

    @Schema(description = "Total number of verified reviews", example = "12")
    @Column(name = "review_count")
    @Builder.Default
    private Integer reviewCount = 0;

    @Schema(description = "Product preview image URL")
    @Column(name = "image_url")
    private String imageUrl;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();
}

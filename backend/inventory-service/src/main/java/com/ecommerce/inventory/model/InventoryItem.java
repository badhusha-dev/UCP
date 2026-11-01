package com.ecommerce.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_item", uniqueConstraints = @UniqueConstraint(name = "ux_inventory_item_tenant_product", columnNames = {"tenant_id", "product_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(length = 64)
    private String sku;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Column(name = "reorder_threshold", nullable = false)
    @Builder.Default
    private Integer reorderThreshold = 10;

    @Column(name = "cost_price", nullable = false, precision = 38, scale = 4)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "warehouse_location", length = 128)
    private String warehouseLocation;

    @Column(name = "unavailable_quantity", nullable = false)
    @Builder.Default
    private Integer unavailableQuantity = 0;

    @Column(name = "incoming_quantity", nullable = false)
    @Builder.Default
    private Integer incomingQuantity = 0;

    @Column(length = 80)
    private String category;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Transient
    public int getAvailableQuantity() {
        int avail = (stockQuantity != null ? stockQuantity : 0) - (reservedQuantity != null ? reservedQuantity : 0) - (unavailableQuantity != null ? unavailableQuantity : 0);
        return Math.max(0, avail);
    }

    @Transient
    public boolean isLowStock() {
        return getAvailableQuantity() <= (reorderThreshold != null ? reorderThreshold : 10);
    }
}

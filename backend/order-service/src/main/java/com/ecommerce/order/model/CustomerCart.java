package com.ecommerce.order.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_cart")
@Data
@Schema(description = "Server-side synchronized customer cart for multi-device persistence and recovery")
public class CustomerCart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    @Schema(description = "Customer user ID", example = "2")
    private Long userId;

    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    @Schema(description = "Serialized cart items JSON array")
    private String itemsJson;

    @Column(nullable = false, length = 32)
    @Schema(description = "Cart status: ACTIVE, ABANDONED, CONVERTED", example = "ACTIVE")
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}

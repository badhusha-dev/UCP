package com.ecommerce.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "back_in_stock_alert")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackInStockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, NOTIFIED

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

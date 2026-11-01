package com.ecommerce.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation", uniqueConstraints = @UniqueConstraint(name = "ux_stock_reservation_idem", columnNames = {"tenant_id", "idempotency_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reservation_id", nullable = false, unique = true, length = 64)
    private String reservationId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "HOLD"; // HOLD, CONFIRMED, RELEASED, EXPIRED

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "backorder_quantity", nullable = false)
    @Builder.Default
    private Integer backorderQuantity = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

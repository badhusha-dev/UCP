package com.ecommerce.stock.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "stock_movement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer delta;

    @Column(name = "movement_type", nullable = false, length = 32)
    private String movementType; // ADJUSTMENT, RECEIVE, RESERVATION_CONFIRM, RETURN_RESTOCK

    @Column(length = 255)
    private String reason;

    @Column(name = "performed_by", length = 128)
    private String performedBy;

    @Column(name = "reference_id", length = 150, unique = true)
    private String referenceId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

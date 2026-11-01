package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "order_return")
@Data
@Schema(description = "Customer return request entity tracking item return status and refund resolution")
public class OrderReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    @Schema(description = "Customer user ID who requested the return", example = "2")
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Schema(description = "Customer explanation / reason for return", example = "Item defective or wrong size")
    private String reason;

    @Column(nullable = false, length = 32)
    @Schema(description = "Return status: REQUESTED, APPROVED, RECEIVED, REFUNDED, REJECTED", example = "REQUESTED")
    private String status = "REQUESTED";

    @Column(name = "refund_amount", nullable = false, precision = 38, scale = 2)
    @Schema(description = "Refund amount eligible for this return", example = "199.99")
    private BigDecimal refundAmount;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    @Schema(description = "Admin or warehouse resolution notes")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}

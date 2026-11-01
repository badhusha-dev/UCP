package com.ecommerce.product.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, length = 32, updatable = false)
    private String action;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(name = "actor_email", length = 255, updatable = false)
    private String actorEmail;

    @Column(name = "before_value", columnDefinition = "TEXT", updatable = false)
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "TEXT", updatable = false)
    private String afterValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

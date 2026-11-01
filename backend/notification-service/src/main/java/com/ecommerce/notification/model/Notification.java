package com.ecommerce.notification.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@Schema(description = "Notification entity storing asynchronous user alerts triggered by platform events")
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Notification unique ID", example = "301")
    private Long id;

    @Column(nullable = false)
    @Schema(description = "Recipient user identifier", example = "1")
    private Long userId;

    @Column(nullable = false)
    @Schema(description = "Event type category", example = "ORDER_CREATED")
    private String type; // ORDER_CREATED, ORDER_CANCELLED, etc.

    @Column(nullable = false)
    @Schema(description = "Human-readable notification message", example = "Order #501 placed successfully with 2 items!")
    private String message;

    @Column(nullable = false)
    @Schema(description = "Read receipt status flag", example = "false")
    private boolean isRead = false;

    @Column(nullable = false)
    @Schema(description = "Notification creation timestamp", example = "2026-10-20T10:30:05")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(unique = true)
    @Schema(description = "Unique idempotency key preventing duplicate event processing", example = "ORDER-501-ORDER_CREATED")
    private String idempotencyKey;
}

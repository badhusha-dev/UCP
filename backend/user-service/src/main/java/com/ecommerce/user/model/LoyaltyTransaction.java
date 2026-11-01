package com.ecommerce.user.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "loyalty_transaction")
@Data
@Schema(description = "Audit trail entry for points earned, redeemed, or adjusted")
public class LoyaltyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Schema(description = "User unique ID", example = "2")
    private Long userId;

    @Column(nullable = false)
    @Schema(description = "Points change (positive for earnings, negative for redemptions)", example = "100")
    private Integer points;

    @Column(name = "transaction_type", nullable = false, length = 32)
    @Schema(description = "Transaction type: EARN, REDEEM, BONUS", example = "EARN")
    private String transactionType;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Description or reference", example = "Points earned from Order #501")
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

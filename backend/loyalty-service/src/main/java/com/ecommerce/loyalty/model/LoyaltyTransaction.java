package com.ecommerce.loyalty.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_transactions")
public class LoyaltyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loyalty_account_id", nullable = false)
    private Long loyaltyAccountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "description", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        EARN, REDEEM, BONUS, ADJUST, EXPIRE
    }

    public LoyaltyTransaction() {}

    public LoyaltyTransaction(Long loyaltyAccountId, Long userId, Integer points,
                               TransactionType transactionType, String referenceId, String description) {
        this.loyaltyAccountId = loyaltyAccountId;
        this.userId = userId;
        this.points = points;
        this.transactionType = transactionType;
        this.referenceId = referenceId;
        this.description = description;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLoyaltyAccountId() { return loyaltyAccountId; }
    public void setLoyaltyAccountId(Long loyaltyAccountId) { this.loyaltyAccountId = loyaltyAccountId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

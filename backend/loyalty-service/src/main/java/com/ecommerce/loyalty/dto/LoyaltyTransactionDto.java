package com.ecommerce.loyalty.dto;

import com.ecommerce.loyalty.model.LoyaltyTransaction;
import java.time.LocalDateTime;

public class LoyaltyTransactionDto {
    private Long id;
    private Integer points;
    private LoyaltyTransaction.TransactionType transactionType;
    private String referenceId;
    private String description;
    private LocalDateTime createdAt;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public LoyaltyTransaction.TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(LoyaltyTransaction.TransactionType transactionType) { this.transactionType = transactionType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

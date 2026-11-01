package com.ecommerce.order.accounting.dto;

import com.ecommerce.order.accounting.model.EntryType;
import com.ecommerce.order.accounting.model.TransactionStatus;
import com.ecommerce.order.accounting.model.TransactionType;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransactionDto {
    private Long id;
    private Long userId;
    private TransactionType transactionType;
    private EntryType entryType;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private String description;
    private String referenceType;
    private String referenceId;
    private String idempotencyKey;
    private Long reversalOfId;
    private String reason;
    private String createdBy;
    private LocalDateTime createdAt;
    private String customerEmail;
    private String reconciliationStatus;
    private String reconciliationNotes;
    private String paymentProvider;
    private String providerReference;
}

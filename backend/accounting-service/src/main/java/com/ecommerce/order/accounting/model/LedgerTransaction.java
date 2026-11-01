package com.ecommerce.order.accounting.model;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Set by Hibernate from AccountingTenantResolver on insert and added to every query (Flyway V19).
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private java.util.UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50, updatable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.POSTED;

    @Column(name = "description", nullable = false, length = 255, updatable = false)
    private String description;

    @Column(name = "reference_type", nullable = false, length = 50, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, length = 100, updatable = false)
    private String referenceId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150, updatable = false)
    private String idempotencyKey;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by", nullable = false, length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 20)
    @Builder.Default
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.RECONCILED;

    @Column(name = "reconciliation_notes", columnDefinition = "TEXT")
    private String reconciliationNotes;

    @Column(name = "payment_provider", length = 50)
    @Builder.Default
    private String paymentProvider = "SIMULATED_INTERNAL";

    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "USD";
        }
        if (this.status == null) {
            this.status = TransactionStatus.POSTED;
        }
        if (this.reconciliationStatus == null) {
            this.reconciliationStatus = ReconciliationStatus.RECONCILED;
        }
        if (this.paymentProvider == null) {
            this.paymentProvider = "SIMULATED_INTERNAL";
        }
    }
}

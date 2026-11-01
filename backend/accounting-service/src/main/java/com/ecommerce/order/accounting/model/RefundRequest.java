package com.ecommerce.order.accounting.model;

import com.ecommerce.order.model.Order;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Set by Hibernate from AccountingTenantResolver on insert and added to every query (Flyway V19).
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private java.util.UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RefundRequestStatus status = RefundRequestStatus.REQUESTED;

    @Column(name = "requested_by", nullable = false, length = 150)
    private String requestedBy;

    @Column(name = "approved_by", length = 150)
    private String approvedBy;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "ledger_transaction_id")
    private Long ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
        if (this.currency == null) {
            this.currency = "USD";
        }
        if (this.status == null) {
            this.status = RefundRequestStatus.REQUESTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

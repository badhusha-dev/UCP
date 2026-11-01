package com.ecommerce.order.accounting.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_accounting_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAccountingPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email_receipts", nullable = false)
    @Builder.Default
    private Boolean emailReceipts = true;

    @Column(name = "statement_notifications", nullable = false)
    @Builder.Default
    private Boolean statementNotifications = false;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
        if (this.emailReceipts == null) this.emailReceipts = true;
        if (this.statementNotifications == null) this.statementNotifications = false;
    }
}

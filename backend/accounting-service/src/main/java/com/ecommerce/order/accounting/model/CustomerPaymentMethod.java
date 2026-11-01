package com.ecommerce.order.accounting.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_payment_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider", nullable = false, length = 50)
    @Builder.Default
    private String provider = "SIMULATED_GATEWAY";

    @Column(name = "payment_token", nullable = false, length = 100)
    private String paymentToken;

    @Column(name = "card_brand", nullable = false, length = 50)
    private String cardBrand;

    @Column(name = "last4", nullable = false, length = 4)
    private String last4;

    @Column(name = "exp_month", nullable = false)
    private Integer expMonth;

    @Column(name = "exp_year", nullable = false)
    private Integer expYear;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.provider == null) {
            this.provider = "SIMULATED_GATEWAY";
        }
        if (this.isDefault == null) {
            this.isDefault = false;
        }
    }
}

package com.ecommerce.cart.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "abandoned_cart_reminders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbandonedCartReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    @JsonIgnore
    private Cart cart;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "discount_code", length = 64)
    private String discountCode;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = new BigDecimal("15.00");

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "SENT"; // SENT, RECOVERED, EXPIRED

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}

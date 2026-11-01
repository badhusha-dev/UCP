package com.ecommerce.order.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "coupon")
@Data
@Schema(description = "Promotional coupon code offering percentage or fixed discounts")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    @Schema(description = "Unique alphanumeric promotional coupon code", example = "WELCOME10")
    private String code;

    @Column(name = "discount_type", nullable = false, length = 32)
    @Schema(description = "Discount calculation method: PERCENTAGE or FIXED", example = "PERCENTAGE")
    private String discountType; // PERCENTAGE, FIXED

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    @Schema(description = "Discount rate (e.g. 10.00 for 10%) or fixed discount amount", example = "10.0000")
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 4)
    @Schema(description = "Minimum eligible basket order amount", example = "20.0000")
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "max_discount", precision = 19, scale = 4)
    @Schema(description = "Maximum discount cap for percentage discounts", example = "50.0000")
    private BigDecimal maxDiscount;

    @Column(name = "usage_limit", nullable = false)
    @Schema(description = "Maximum redemption times for this promotion", example = "1000")
    private Integer usageLimit = 1000;

    @Column(name = "times_used", nullable = false)
    @Schema(description = "Times this coupon has been successfully redeemed", example = "15")
    private Integer timesUsed = 0;

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "expiry_date")
    private OffsetDateTime expiryDate;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

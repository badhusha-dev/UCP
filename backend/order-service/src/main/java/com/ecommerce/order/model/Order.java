package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@Schema(description = "Customer order entity containing items, status, and pricing totals")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Order unique ID", example = "501")
    private Long id;

    @Schema(description = "User ID who placed the order", example = "1")
    private Long userId;

    @Schema(description = "Current lifecycle status of the order", example = "CONFIRMED", allowableValues = {"PENDING", "CONFIRMED", "PENDING_INVENTORY", "CANCELLED"})
    private String status;

    @Schema(description = "Total purchase price amount", example = "399.98")
    private BigDecimal totalAmount;

    @Schema(description = "Discount amount applied from coupon or promotion", example = "15.00")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Schema(description = "Applied promotional coupon code", example = "WELCOME10")
    private String couponCode;

    @Schema(description = "Pricing-service redemption reference when promotions were applied at checkout")
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.persistence.Column(name = "promotion_ref", length = 64)
    private String promotionRef;

    @Schema(description = "Fulfillment carrier", example = "FedEx")
    private String carrier = "Standard Shipping";

    @Schema(description = "Shipment tracking number", example = "TRK-98721345")
    private String trackingNumber;

    @Schema(description = "Estimated delivery date", example = "2026-10-28T18:00:00")
    private LocalDateTime estimatedDelivery;

    @Schema(description = "Order shipping and fulfillment status", example = "PROCESSING")
    private String shippingStatus = "PROCESSING";

    @Schema(description = "Order creation timestamp", example = "2026-10-20T10:30:00")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    @ArraySchema(schema = @Schema(implementation = OrderItem.class), arraySchema = @Schema(description = "List of line items in this order"))
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<OrderTrackingEvent> trackingEvents = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<OrderReturn> returns = new ArrayList<>();

    // ---- Order Management Control Tower fields (hidden from the legacy customer JSON; exposed only through the admin API) ----
    public static final java.util.UUID LEGACY_TENANT_ID = java.util.UUID.fromString("00000000-0000-4000-8000-000000000001");

    @JsonIgnore @Column(name = "tenant_id", nullable = false, updatable = false) private java.util.UUID tenantId = LEGACY_TENANT_ID;
    @JsonIgnore @Column(name = "store_id", nullable = false) private String storeId = "default";
    @JsonIgnore private String vendorId;
    @JsonIgnore @Column(name = "order_type", nullable = false) private String orderType = "RETAIL";
    @JsonIgnore private String customerName;
    @JsonIgnore private String customerEmail;
    @JsonIgnore private String customerPhone;
    @JsonIgnore private String deliveryAddress;
    @JsonIgnore private String specialInstructions;
    @JsonIgnore @Column(name = "payment_status", nullable = false) private String paymentStatus = "UNKNOWN";
    @JsonIgnore private String paymentMethod;
    @JsonIgnore private String paymentReference;
    @JsonIgnore @Column(name = "fulfillment_status", nullable = false) private String fulfillmentStatus = "UNFULFILLED";
    @JsonIgnore private Long exchangeOfReturnId;
    @JsonIgnore @Column(name = "exchange_payment_required", nullable = false) private boolean exchangePaymentRequired = false;
    @JsonIgnore @Column(name = "refund_status", nullable = false) private String refundStatus = "NONE";
    @JsonIgnore private String refundReference;
    @JsonIgnore @Column(name = "tax_amount", nullable = false) private BigDecimal taxAmount = BigDecimal.ZERO;
    @JsonIgnore @Column(name = "delivery_charge", nullable = false) private BigDecimal deliveryCharge = BigDecimal.ZERO;
    @JsonIgnore private String deliveryPartnerName;
    @JsonIgnore private String deliveryReference;
    @JsonIgnore private Long assignedOperatorId;
    @JsonIgnore private String holdReason;
    @JsonIgnore private String statusBeforeHold;
    @JsonIgnore private Integer prepMinutes;
    @JsonIgnore private String rejectionReason;
    @JsonIgnore private java.time.OffsetDateTime confirmedAt;
    @JsonIgnore private java.time.OffsetDateTime processingAt;
    @JsonIgnore private java.time.OffsetDateTime readyAt;
    @JsonIgnore private java.time.OffsetDateTime shippedAt;
    @JsonIgnore private java.time.OffsetDateTime deliveredAt;
    @JsonIgnore private java.time.OffsetDateTime cancelledAt;
    @JsonIgnore @Column(name = "status_changed_at", nullable = false) private java.time.OffsetDateTime statusChangedAt = java.time.OffsetDateTime.now();
    /** Optimistic lock: concurrent administrative changes to one order cannot silently overwrite each other. */
    @JsonIgnore @Version private Long version;

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}

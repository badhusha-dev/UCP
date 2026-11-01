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
    @ArraySchema(schema = @Schema(implementation = OrderItem.class), arraySchema = @Schema(description = "List of line items in this order"))
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<OrderTrackingEvent> trackingEvents = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<OrderReturn> returns = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}

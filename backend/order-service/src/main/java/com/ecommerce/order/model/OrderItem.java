package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@Schema(description = "Individual line item belonging to a customer order")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Order line item unique ID", example = "1001")
    private Long id;

    @Schema(description = "Referenced product ID", example = "101")
    private Long productId;

    @Schema(description = "Product display name at time of order", example = "Wireless Headphones")
    private String productName;

    @Schema(description = "Unit price at time of purchase", example = "199.99")
    private BigDecimal price;

    @Schema(description = "Quantity purchased", example = "2")
    private Integer quantity;

    @JsonIgnore private String vendorId;
    @JsonIgnore @Column(name = "allocated_quantity", nullable = false) private Integer allocatedQuantity = 0;
    @JsonIgnore @Column(name = "fulfilled_quantity", nullable = false) private Integer fulfilledQuantity = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;
}

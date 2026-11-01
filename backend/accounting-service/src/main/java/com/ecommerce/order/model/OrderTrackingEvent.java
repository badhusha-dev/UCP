package com.ecommerce.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Entity
@Table(name = "order_tracking_event")
@Data
@Schema(description = "Milestone event in the order shipping and delivery journey")
public class OrderTrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, length = 64)
    @Schema(description = "Milestone status: PLACED, PROCESSING, SHIPPED, OUT_FOR_DELIVERY, DELIVERED", example = "SHIPPED")
    private String status;

    @Column(length = 255)
    @Schema(description = "Physical or sorting hub location", example = "Memvis Logistics Hub, TN")
    private String location;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Detailed milestone description", example = "Package departed carrier facility")
    private String description;

    @Column(name = "event_timestamp", nullable = false)
    private OffsetDateTime eventTimestamp = OffsetDateTime.now();
}

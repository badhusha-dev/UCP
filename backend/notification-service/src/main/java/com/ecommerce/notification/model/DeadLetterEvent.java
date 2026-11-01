package com.ecommerce.notification.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dead Letter Queue event entity persisting poisoned or exhausted Kafka messages")
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Dead letter record unique ID", example = "1")
    private Long id;

    @Column(nullable = false)
    @Schema(description = "Kafka origin topic where event failed", example = "order-events")
    private String topic;

    @Schema(description = "Kafka partition ID where event was received", example = "0")
    private Integer partitionId;

    @Schema(description = "Kafka offset number in partition", example = "42")
    private Long offsetNumber;

    @Column(columnDefinition = "TEXT", nullable = false)
    @Schema(description = "Raw JSON payload of the message", example = "{\"orderId\":501,\"status\":\"FAILED\"}")
    private String payload;

    @Column(columnDefinition = "TEXT")
    @Schema(description = "Captured exception trace or failure reason", example = "NullPointerException: orderId must not be null")
    private String exceptionMessage;

    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "Processing status of dead letter event", example = "FAILED", allowableValues = {"FAILED", "REPLAYED", "RESOLVED"})
    private String status = "FAILED"; // FAILED, REPLAYED, RESOLVED

    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "Timestamp when message landed in DLQ", example = "2026-10-20T10:31:00")
    private LocalDateTime createdAt = LocalDateTime.now();
}

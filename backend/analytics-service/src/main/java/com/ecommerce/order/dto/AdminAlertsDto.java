package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Operational health, bottleneck detection, and anomaly notifications for administrators")
public class AdminAlertsDto {
    @Schema(description = "Count of orders remaining in PENDING or PROCESSING status longer than 24 hours", example = "2")
    private Long delayedFulfillmentCount;

    @Schema(description = "Count of customer carts inactive between 2 and 72 hours", example = "5")
    private Long abandonedCartsCount;

    @Schema(description = "Flag indicating cancellation rate exceeds tolerance threshold (e.g., > 10%)", example = "false")
    private Boolean highCancellationRateAlert;

    @Schema(description = "Current cancellation rate across the last 7 days", example = "4.2")
    private Double currentCancellationRate;

    @Schema(description = "List of active operational warning messages")
    private List<String> warnings;
}

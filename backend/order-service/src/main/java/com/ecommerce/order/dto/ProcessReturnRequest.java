package com.ecommerce.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Admin processing decision for a return request")
public class ProcessReturnRequest {

    @NotBlank(message = "Action is mandatory: APPROVE, REJECT, or REFUND")
    @Schema(description = "Decision action: APPROVE, REJECT, or REFUND", example = "REFUND", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action; // APPROVE, REJECT, REFUND

    @Schema(description = "Administrative resolution notes", example = "Item inspected at warehouse and refund approved")
    private String resolutionNotes;

    @Schema(description = "Refund amount to disburse (if action is REFUND)", example = "49.99")
    private BigDecimal refundAmount;

    @Schema(description = "Whether inspected returned goods should be added back to available stock", example = "true")
    private Boolean restock;
}

package com.ecommerce.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Customer return request payload")
public class CreateReturnRequest {

    @NotBlank(message = "Reason is mandatory for return request")
    @Schema(description = "Customer stated reason for returning item(s)", example = "Item arrived damaged or incorrect size", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;

    @NotEmpty(message = "Select at least one order item to return")
    @Valid
    @Schema(description = "Specific purchased items and quantities requested for return", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ReturnLineRequest> items;

    @Schema(description = "Policy reason code (defaults to OTHER)", example = "DEFECTIVE")
    private String reasonCode;

    @Schema(description = "REFUND (default), EXCHANGE or STORE_CREDIT")
    private String resolution;

    @Schema(description = "Customer-reported condition: NEW, LIKE_NEW, USED, DAMAGED or DEFECTIVE")
    private String condition;

    @Deprecated
    @JsonIgnore
    private BigDecimal refundAmount;
}

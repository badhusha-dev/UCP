package com.ecommerce.order.accounting.dto;

import com.ecommerce.order.accounting.model.TransactionType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualAdjustmentRequest {

    @NotNull(message = "Customer user ID is required")
    private Long userId;

    @NotNull(message = "Transaction type is required (MANUAL_CREDIT, MANUAL_DEBIT, ADJUSTMENT, ORDER_REFUND)")
    private TransactionType transactionType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "USD";

    @NotBlank(message = "Reason is required for auditable adjustments")
    private String reason;

    private String reference;

    private String referenceType;

    private String referenceId;

    public String getReference() {
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return referenceId;
    }

    public String getReferenceType() {
        if (referenceType != null && !referenceType.isBlank()) {
            return referenceType;
        }
        return "MANUAL";
    }

    private String idempotencyKey;
}

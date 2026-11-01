package com.ecommerce.order.accounting.dto;

import com.ecommerce.order.accounting.model.ReconciliationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationUpdateRequest {

    @NotNull(message = "Reconciliation status is required")
    private ReconciliationStatus status;

    private String notes;
}

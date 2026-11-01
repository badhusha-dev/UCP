package com.ecommerce.order.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReversalRequest {

    @NotBlank(message = "Reason for transaction reversal is required")
    private String reason;
}

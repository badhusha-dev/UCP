package com.ecommerce.order.accounting.dto;

import com.ecommerce.order.accounting.model.RefundRequestStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequestDto {
    private Long id;
    private Long orderId;
    private Long userId;
    private String customerEmail;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private RefundRequestStatus status;
    private String requestedBy;
    private String approvedBy;
    private String decisionReason;
    private Long ledgerTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

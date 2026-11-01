package com.ecommerce.order.accounting.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingAuditLogDto {
    private Long id;
    private Long transactionId;
    private Long adminId;
    private String adminEmail;
    private String action;
    private String reason;
    private String beforeValue;
    private String afterValue;
    private LocalDateTime createdAt;
}

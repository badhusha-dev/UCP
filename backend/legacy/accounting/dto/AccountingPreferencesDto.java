package com.ecommerce.order.accounting.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingPreferencesDto {
    private Long userId;
    private Boolean emailReceipts;
    private Boolean statementNotifications;
}

package com.ecommerce.order.accounting.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceDto {
    private Long userId;
    private String currency;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal netBalance;
    private String explanation;
}

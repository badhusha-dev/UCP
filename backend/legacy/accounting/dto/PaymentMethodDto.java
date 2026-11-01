package com.ecommerce.order.accounting.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodDto {
    private Long id;
    private String provider;
    private String cardBrand;
    private String last4;
    private Integer expMonth;
    private Integer expYear;
    private Boolean isDefault;
    private String label;
}

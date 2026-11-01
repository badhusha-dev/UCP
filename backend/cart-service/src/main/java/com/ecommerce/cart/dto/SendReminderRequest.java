package com.ecommerce.cart.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendReminderRequest {
    @Builder.Default
    private String discountCode = "COMEBACK15";

    @Builder.Default
    private BigDecimal discountPercent = new BigDecimal("15.00");
}

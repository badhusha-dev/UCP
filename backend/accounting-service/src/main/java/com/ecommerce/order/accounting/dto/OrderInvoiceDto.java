package com.ecommerce.order.accounting.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderInvoiceDto {
    private String invoiceNumber;
    private Long orderId;
    private LocalDateTime orderDate;
    private Long userId;
    private String customerEmail;
    private String billingAddress;

    private List<InvoiceLineItemDto> items;

    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String currency;

    private String orderStatus;
    private String paymentStatus;
    private String paymentMethodLabel;
    private String paymentReference;

    private String disclaimer;
}

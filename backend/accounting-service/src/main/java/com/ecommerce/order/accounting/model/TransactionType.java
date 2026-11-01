package com.ecommerce.order.accounting.model;

public enum TransactionType {
    ORDER_PAYMENT,
    ORDER_REFUND,
    MANUAL_CREDIT,
    MANUAL_DEBIT,
    ADJUSTMENT,
    REVERSAL,
    PAYMENT_FAILURE,
    STORE_CREDIT_TOPUP
}

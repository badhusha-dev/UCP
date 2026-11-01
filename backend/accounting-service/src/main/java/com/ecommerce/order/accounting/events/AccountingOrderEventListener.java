package com.ecommerce.order.accounting.events;

import com.ecommerce.order.accounting.service.AccountingService;
import com.ecommerce.order.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountingOrderEventListener {

    private final ObjectMapper objectMapper;
    private final AccountingService accountingService;

    @KafkaListener(topics = "order-events", groupId = "accounting-service")
    public void onOrderEvent(String payload) throws Exception {
        var event = objectMapper.readTree(payload);
        String type = text(event, "eventType");
        Long orderId = number(event, "orderId");
        Long userId = number(event, "userId");
        BigDecimal amount = decimal(event, "totalAmount");
        if (orderId == null || amount == null) {
            log.warn("Ignoring accounting event without an order ID or amount: {}", payload);
            return;
        }

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setTotalAmount(amount);

        switch (type == null ? "" : type) {
            case "ORDER_CREATED", "ORDER_STATUS_UPDATED" -> {
                if ("PLACED".equalsIgnoreCase(text(event, "status"))) {
                    accountingService.recordOrderPayment(order);
                }
            }
            case "ORDER_CANCELLED" -> accountingService.recordOrderRefund(
                    order, "Order #" + orderId + " cancellation refund");
            case "ORDER_RETURN_REFUND" -> accountingService.recordOrderRefund(
                    order,
                    text(event, "reason"),
                    "ORDER_RETURN_REFUND_" + text(event, "referenceId"));
            default -> log.debug("Accounting service ignored order event type {}", type);
        }
    }

    private String text(com.fasterxml.jackson.databind.JsonNode event, String field) {
        var value = event.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long number(com.fasterxml.jackson.databind.JsonNode event, String field) {
        var value = event.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private BigDecimal decimal(com.fasterxml.jackson.databind.JsonNode event, String field) {
        var value = event.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }
}

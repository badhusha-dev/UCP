package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderProducerServiceTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");

    private OutboxWriter outbox;
    private OrderRepository orders;
    private ObjectMapper mapper;
    private OrderProducerService producer;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxWriter.class);
        orders = mock(OrderRepository.class);
        mapper = new ObjectMapper();
        producer = new OrderProducerService(outbox, mapper, orders);
    }

    private OrderEvent event() {
        OrderEvent event = new OrderEvent();
        event.setEventType("ORDER_CREATED");
        event.setOrderId(12L);
        event.setUserId(3L);
        event.setStatus("CREATED");
        event.setTotalAmount(new BigDecimal("49.90"));
        return event;
    }

    @Test
    @DisplayName("Order events go to the outbox on order-events, keyed by order id, carrying the order's tenant")
    void recordsOrderEventWithTenantFromTheOrder() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(outbox.append(any(UUID.class), anyString(), any(), anyString(), anyString())).thenReturn(eventId);
        Order order = new Order();
        order.setTenantId(TENANT);
        when(orders.findById(12L)).thenReturn(Optional.of(order));

        assertThat(producer.publishOrderEvent(event())).isEqualTo(eventId);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).append(eq(TENANT), eq("order-events"), eq("12"), eq("ORDER_CREATED"), payload.capture());
        assertThat(mapper.readTree(payload.getValue()).get("tenantId").asText()).isEqualTo(TENANT.toString());
        assertThat(mapper.readTree(payload.getValue()).get("orderId").asLong()).isEqualTo(12L);
    }

    @Test
    @DisplayName("A tenant set by the caller is kept without looking the order up")
    void keepsTheCallersTenant() throws Exception {
        OrderEvent event = event();
        event.setTenantId(TENANT);

        producer.publishOrderEvent(event);

        verify(orders, never()).findById(anyLong());
        verify(outbox).append(eq(TENANT), eq("order-events"), eq("12"), eq("ORDER_CREATED"), anyString());
    }
}

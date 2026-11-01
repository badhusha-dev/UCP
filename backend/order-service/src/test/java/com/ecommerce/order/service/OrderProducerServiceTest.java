package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderProducerServiceTest {

    @Test
    @DisplayName("Order events go to the outbox on order-events, keyed by order id, with the same JSON as before")
    void recordsOrderEventInOutbox() throws Exception {
        OutboxWriter outbox = mock(OutboxWriter.class);
        UUID eventId = UUID.randomUUID();
        when(outbox.append(anyString(), any(), anyString(), anyString())).thenReturn(eventId);
        ObjectMapper mapper = new ObjectMapper();
        OrderProducerService producer = new OrderProducerService(outbox, mapper);

        OrderEvent event = new OrderEvent();
        event.setEventType("ORDER_CREATED");
        event.setOrderId(12L);
        event.setUserId(3L);
        event.setStatus("CREATED");
        event.setTotalAmount(new BigDecimal("49.90"));

        assertThat(producer.publishOrderEvent(event)).isEqualTo(eventId);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).append(eq("order-events"), eq("12"), eq("ORDER_CREATED"), payload.capture());
        assertThat(payload.getValue()).isEqualTo(mapper.writeValueAsString(event));
    }
}

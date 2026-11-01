package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records order events in the transactional outbox; {@code ucp-outbox} publishes them to {@code order-events} after
 * the caller's transaction commits. The JSON payload and the message key (order id) are the same as when this
 * service sent directly, and the topic's StringSerializer template still sends them, so consumers see no change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProducerService {
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;
    static final String TOPIC = "order-events";

    /** Returns the outbox event id, or null when the event could not be serialised. */
    public UUID publishOrderEvent(OrderEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String messageKey = event.getOrderId() != null ? String.valueOf(event.getOrderId()) : null;
            String eventType = event.getEventType() != null ? event.getEventType() : "ORDER_EVENT";
            UUID eventId = outbox.append(TOPIC, messageKey, eventType, payload);
            log.info("Recorded {} for order [{}] in the outbox as event {}", eventType, event.getOrderId(), eventId);
            return eventId;
        } catch (JsonProcessingException e) {
            log.error("Error serializing order event for order ID: {}", event.getOrderId(), e);
            return null;
        }
    }
}

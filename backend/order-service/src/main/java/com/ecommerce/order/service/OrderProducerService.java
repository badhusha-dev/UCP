package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderEvent;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records order events in the transactional outbox; {@code ucp-outbox} publishes them to {@code order-events} after
 * the caller's transaction commits. The message key is the order id and the topic's StringSerializer template sends
 * the JSON, as before.
 *
 * <p>Every event carries the order's {@code tenantId}: callers may set it, otherwise it is read from the order (a
 * primary-key lookup, usually served from the persistence context). The outbox row records the same tenant. The
 * field is additive; consumers read the payload as a JSON tree or ignore unknown properties.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProducerService {
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    static final String TOPIC = "order-events";

    /** Returns the outbox event id, or null when the event could not be serialised. */
    public UUID publishOrderEvent(OrderEvent event) {
        if (event.getTenantId() == null && event.getOrderId() != null) {
            orderRepository.findById(event.getOrderId()).map(Order::getTenantId).ifPresent(event::setTenantId);
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            String messageKey = event.getOrderId() != null ? String.valueOf(event.getOrderId()) : null;
            String eventType = event.getEventType() != null ? event.getEventType() : "ORDER_EVENT";
            UUID eventId = outbox.append(event.getTenantId(), TOPIC, messageKey, eventType, payload);
            log.info("Recorded {} for order [{}] (tenant {}) in the outbox as event {}", eventType, event.getOrderId(),
                    event.getTenantId(), eventId);
            return eventId;
        } catch (JsonProcessingException e) {
            log.error("Error serializing order event for order ID: {}", event.getOrderId(), e);
            return null;
        }
    }
}

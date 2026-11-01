package com.ecommerce.inventory.service;

import com.ecommerce.inventory.config.KafkaConfig;
import com.ecommerce.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records inventory events in the transactional outbox; {@code ucp-outbox} publishes them to {@code inventory.events}
 * after the caller's transaction commits. The topic used to receive events through Spring Kafka's JsonSerializer, so
 * the JSON is produced with the same mapper and sent RAW: product-service reads exactly the bytes it read before.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryProducerService {

    private static final ObjectMapper WIRE_MAPPER = JacksonUtils.enhancedObjectMapper();

    private final OutboxWriter outbox;

    public void publishInventoryEvent(String eventType, Long productId, Object details) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("aggregateId", String.valueOf(productId));
        event.put("timestamp", OffsetDateTime.now().toString());
        event.put("payload", details);
        try {
            outbox.appendRaw(KafkaConfig.INVENTORY_EVENTS_TOPIC, String.valueOf(productId), eventType, wireJson(event));
            log.info("Recorded inventory event [{}] for Product #{} in the outbox", eventType, productId);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise inventory event [{}] for Product #{}: {}", eventType, productId, e.getMessage());
        }
    }

    /** The JSON that Spring Kafka's JsonSerializer (default configuration) writes for this value. */
    static String wireJson(Object value) throws JsonProcessingException {
        return WIRE_MAPPER.writeValueAsString(value);
    }
}

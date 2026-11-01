package com.ecommerce.stock.service;

import com.ecommerce.stock.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInventoryEvent(String eventType, Long productId, Object details) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("eventType", eventType);
            event.put("aggregateId", String.valueOf(productId));
            event.put("timestamp", OffsetDateTime.now().toString());
            event.put("payload", details);

            kafkaTemplate.send(KafkaConfig.INVENTORY_EVENTS_TOPIC, String.valueOf(productId), event);
            log.info("Published inventory event [{}] for Product #{}", eventType, productId);
        } catch (Exception e) {
            log.warn("Kafka event publication failed for [{}] Product #{}: {}", eventType, productId, e.getMessage());
        }
    }
}

package com.ecommerce.order.consumer;

import com.ecommerce.order.OrderDataSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsOrderEventListener {

    private final ObjectMapper objectMapper;
    private final OrderDataSyncService orderDataSyncService;

    @KafkaListener(topics = "order-events", groupId = "analytics-service")
    public void onOrderEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventType = event.has("eventType") ? event.get("eventType").asText() : "UNKNOWN";
            Long orderId = event.has("orderId") && !event.get("orderId").isNull() ? event.get("orderId").asLong() : null;
            log.info("Analytics Service received Kafka event [{}] for orderId [{}]. Triggering read-model projection sync...", eventType, orderId);

            orderDataSyncService.syncSnapshot();
        } catch (Exception ex) {
            log.warn("Failed to process order event in Analytics Service: {}", ex.getMessage());
        }
    }
}

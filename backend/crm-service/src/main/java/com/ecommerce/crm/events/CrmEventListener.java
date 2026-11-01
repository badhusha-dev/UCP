package com.ecommerce.crm.events;

import com.ecommerce.crm.service.CrmService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrmEventListener {
    private final CrmService service;
    @KafkaListener(topics = {"order-events", "user-events", "loyalty.tier.upgraded", "loyalty.points.earned"}, groupId = "crm-service")
    public void onEvent(String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        service.captureEvent(topic, key, partition, offset, payload);
    }
}

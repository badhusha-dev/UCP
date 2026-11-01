package com.ecommerce.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class DeliveryOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(DeliveryOutboxPublisher.class);
    private final DeliveryOutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    @Value("${delivery.events.topic:delivery-events}")
    private String topic;

    public DeliveryOutboxPublisher(DeliveryOutboxRepository outbox, KafkaTemplate<String, String> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${delivery.outbox-poll-ms:3000}")
    public void publishReadyEvents() {
        for (Map<String, Object> event : outbox.claimBatch()) {
            UUID id = UUID.fromString(String.valueOf(event.get("eventId")));
            try {
                kafka.send(topic, String.valueOf(event.get("aggregateId")), String.valueOf(event.get("payload")))
                        .get(10, TimeUnit.SECONDS);
                outbox.published(id);
            } catch (Exception failure) {
                outbox.failed(id);
                log.warn("Delivery outbox publish failed; event {} will be retried", id, failure);
            }
        }
    }
}

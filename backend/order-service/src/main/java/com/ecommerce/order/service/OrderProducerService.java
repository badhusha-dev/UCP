package com.ecommerce.order.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecommerce.order.dto.OrderEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProducerService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "order-events";

    public CompletableFuture<SendResult<String, String>> publishOrderEvent(OrderEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String messageKey = event.getOrderId() != null ? String.valueOf(event.getOrderId()) : null;
            log.info("Publishing event to topic [{}] with partition key [{}]: {}", TOPIC, messageKey, payload);

            CompletableFuture<SendResult<String, String>> future = this.kafkaTemplate.send(TOPIC, messageKey, payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("CRITICAL: Failed to publish Kafka event for order ID [{}] to topic [{}]: {}",
                            event.getOrderId(), TOPIC, ex.getMessage(), ex);
                } else {
                    log.info("Kafka event acknowledged for order ID [{}]. Topic: {}, Partition: {}, Offset: {}",
                            event.getOrderId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

            return future;
        } catch (JsonProcessingException e) {
            log.error("Error serializing order event for order ID: {}", event.getOrderId(), e);
            CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }
}

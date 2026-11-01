package com.ecommerce.notification.service;

import com.ecommerce.notification.model.DeadLetterEvent;
import com.ecommerce.notification.repository.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterService {

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public List<DeadLetterEvent> getAllDeadLetters(String status) {
        if (status != null && !status.isBlank()) {
            return deadLetterEventRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        return deadLetterEventRepository.findAll();
    }

    public Optional<DeadLetterEvent> getDeadLetterById(Long id) {
        return deadLetterEventRepository.findById(id);
    }

    @Transactional
    public DeadLetterEvent replayDeadLetter(Long id) {
        DeadLetterEvent event = deadLetterEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dead letter event with id " + id + " not found"));

        // Primary destination topic for order events
        String targetTopic = "order-events";
        log.info("Replaying dead-lettered event ID: {} back to topic: {}", id, targetTopic);

        // Republish to Kafka topic
        kafkaTemplate.send(targetTopic, event.getPayload()).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to replay dead letter event ID: {}", id, ex);
            } else {
                log.info("Successfully replayed dead letter event ID: {} to topic: {}, offset: {}",
                        id, targetTopic, result.getRecordMetadata().offset());
            }
        });

        event.setStatus("REPLAYED");
        return deadLetterEventRepository.save(event);
    }
}

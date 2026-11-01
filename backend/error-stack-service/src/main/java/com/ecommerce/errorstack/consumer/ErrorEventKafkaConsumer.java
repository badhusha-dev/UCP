package com.ecommerce.errorstack.consumer;

import com.ecommerce.errorstack.dto.ErrorIngestRequest;
import com.ecommerce.errorstack.service.ErrorStackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ErrorEventKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final ErrorStackService errorStackService;

    @KafkaListener(topics = {"error-events", "application-errors"}, groupId = "error-stack-group")
    public void onErrorEvent(String payload) {
        try {
            log.info("Received Kafka error event payload");
            ErrorIngestRequest request = objectMapper.readValue(payload, ErrorIngestRequest.class);
            errorStackService.ingest(request);
        } catch (Exception ex) {
            log.warn("Failed to process error event from Kafka topic: {}", ex.getMessage());
        }
    }
}

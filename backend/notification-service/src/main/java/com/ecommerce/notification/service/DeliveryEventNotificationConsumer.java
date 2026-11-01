package com.ecommerce.notification.service;

import com.ecommerce.notification.model.Notification;
import com.ecommerce.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryEventNotificationConsumer {
    private final NotificationRepository notifications;
    private final ObjectMapper mapper;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "${delivery.events.topic:delivery-events}", groupId = "notification-delivery-group")
    @Transactional
    public void consume(String payload) {
        try {
            JsonNode event = mapper.readTree(payload);
            String eventId = requiredText(event, "eventId");
            if (!event.hasNonNull("recipientUserId")) return;
            long recipientId = event.get("recipientUserId").asLong();
            if (recipientId <= 0) throw new IllegalArgumentException("Invalid recipientUserId");
            String key = "DELIVERY-" + eventId;
            if (notifications.existsByIdempotencyKey(key)) return;
            String eventType = event.path("eventType").asText("DELIVERY_UPDATE");
            String order = event.path("orderReference").asText("your order");
            String status = event.path("details").path("toStatus").asText("");
            String message = switch (eventType) {
                case "DELIVERY_CREATED" -> "Delivery processing has started for order " + order + ".";
                case "DELIVERY_STATUS_CHANGED" -> status.isBlank()
                        ? "There is an update to the delivery for order " + order + "."
                        : "The delivery for order " + order + " is now " + status.replace('_', ' ').toLowerCase() + ".";
                case "DELIVERY_ASSIGNMENT_OFFERED" -> "A delivery partner is being assigned to order " + order + ".";
                case "DELIVERY_INCIDENT_REPORTED" -> "There is an exception update for the delivery of order " + order + ".";
                default -> "There is a delivery update for order " + order + ".";
            };
            Notification notification = new Notification();
            notification.setUserId(recipientId);
            notification.setType(eventType);
            notification.setMessage(message);
            notification.setIdempotencyKey(key);
            notifications.save(notification);
        } catch (Exception exception) {
            log.error("Could not process Delivery notification event", exception);
            throw new IllegalArgumentException("Delivery event processing failed", exception);
        }
    }

    private static String requiredText(JsonNode node, String name) {
        String value = node.path(name).asText("").trim();
        if (value.isEmpty() || value.length() > 160) throw new IllegalArgumentException("Missing or invalid " + name);
        return value;
    }
}

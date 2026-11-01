package com.ecommerce.outbox;

import java.util.UUID;

/**
 * A stored event awaiting publication. {@code raw} events are sent as plain text; the others go through the
 * service's own KafkaTemplate and its value serializer.
 */
public record OutboxEvent(UUID eventId, String topic, String key, String eventType, String payload, int attemptCount,
                          boolean raw) {
}

package com.ecommerce.outbox;

import java.util.UUID;

/** A stored event awaiting publication. */
public record OutboxEvent(UUID eventId, String topic, String key, String eventType, String payload, int attemptCount) {
}

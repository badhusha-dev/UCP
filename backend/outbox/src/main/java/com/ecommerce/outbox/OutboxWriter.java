package com.ecommerce.outbox;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Records an event in the caller's database transaction. When the caller runs inside {@code @Transactional}, the event
 * commits or rolls back together with the business change; the publisher only ever sees committed events.
 */
public class OutboxWriter {

    private final OutboxRepository repository;
    private final Clock clock;

    public OutboxWriter(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public UUID append(String topic, String key, String eventType, String payload) {
        return append(null, topic, key, eventType, payload);
    }

    public UUID append(UUID tenantId, String topic, String key, String eventType, String payload) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        UUID eventId = UUID.randomUUID();
        repository.insert(eventId, tenantId, topic, key, eventType, payload, clock.instant());
        return eventId;
    }
}

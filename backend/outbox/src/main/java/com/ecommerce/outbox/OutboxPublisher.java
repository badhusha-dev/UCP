package com.ecommerce.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Publishes committed outbox events to Kafka. A failed send is retried with exponential backoff (1 s doubling up to
 * 5 min). An event left IN_FLIGHT by a crashed instance is reclaimed once its lock expires, so delivery is at least
 * once: consumers must tolerate duplicates.
 *
 * <p>Ordinary events go through the service's KafkaTemplate. Raw events go through {@code rawKafka}, a string
 * serializer template built from the same Kafka settings.
 */
public class OutboxPublisher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<Object, Object> kafka;
    private final KafkaTemplate<String, String> rawKafka;
    private final Clock clock;
    private final int batchSize;
    private final Duration sendTimeout;
    private final Duration lockDuration;
    private final Duration retention;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<Object, Object> kafka,
                           KafkaTemplate<String, String> rawKafka, Clock clock, int batchSize, Duration sendTimeout,
                           Duration lockDuration, Duration retention) {
        this.repository = repository;
        this.kafka = kafka;
        this.rawKafka = rawKafka;
        this.clock = clock;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.lockDuration = lockDuration;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${ucp.outbox.poll-interval-ms:2000}")
    public void publishDue() {
        Instant now = clock.instant();
        for (OutboxEvent event : repository.findDue(now, batchSize)) {
            if (!repository.claim(event.eventId(), now, now.plus(lockDuration))) continue; // another instance has it
            try {
                send(event).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                repository.markPublished(event.eventId(), clock.instant());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                repository.markFailed(event.eventId(), clock.instant().plus(backoff(event.attemptCount())));
                return;
            } catch (Exception failure) {
                repository.markFailed(event.eventId(), clock.instant().plus(backoff(event.attemptCount())));
                log.warn("Outbox event {} ({} to {}) not published on attempt {}; it will be retried",
                        event.eventId(), event.eventType(), event.topic(), event.attemptCount() + 1, failure);
            }
        }
        repository.deletePublishedBefore(now.minus(retention));
    }

    private java.util.concurrent.CompletableFuture<?> send(OutboxEvent event) {
        if (!event.raw()) return kafka.send(event.topic(), event.key(), event.payload());
        if (rawKafka == null) throw new IllegalStateException("No raw Kafka producer is configured for RAW outbox events");
        return rawKafka.send(event.topic(), event.key(), event.payload());
    }

    static Duration backoff(int attemptsSoFar) {
        return Duration.ofSeconds(Math.min(300, 1L << Math.min(attemptsSoFar, 9)));
    }

    @Override
    public void destroy() throws Exception {
        if (rawKafka != null && rawKafka.getProducerFactory() instanceof DisposableBean factory) factory.destroy();
    }
}

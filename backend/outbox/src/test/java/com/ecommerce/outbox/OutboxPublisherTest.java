package com.ecommerce.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private MutableClock clock;
    private OutboxRepository repository;
    private OutboxWriter writer;
    private KafkaTemplate<Object, Object> kafka;
    private OutboxPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true)
                .addScript("ucp-outbox/schema.sql").build();
        jdbc = new JdbcTemplate(database);
        clock = new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
        repository = new OutboxRepository(jdbc);
        writer = new OutboxWriter(repository, clock);
        kafka = mock(KafkaTemplate.class);
        publisher = new OutboxPublisher(repository, kafka, clock, 50, Duration.ofSeconds(1), Duration.ofSeconds(60),
                Duration.ofDays(7));
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id = ?", String.class, id.toString());
    }

    @Test
    @DisplayName("A committed event is sent with its topic, key and payload, then marked published")
    void publishesAndMarksPublished() {
        when(kafka.send(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        UUID id = writer.append("cart.events", "42", "CART_UPDATED", "{\"cartId\":42}");

        publisher.publishDue();

        verify(kafka).send("cart.events", "42", "{\"cartId\":42}");
        assertThat(status(id)).isEqualTo("PUBLISHED");
        assertThat(repository.countUnpublished()).isZero();
    }

    @Test
    @DisplayName("A failed send is retried only after the backoff, then succeeds")
    void retriesWithBackoff() {
        when(kafka.send(anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .thenReturn(CompletableFuture.completedFuture(null));
        UUID id = writer.append("loyalty.points.earned", null, "loyalty.points.earned", "{}");

        publisher.publishDue();
        assertThat(status(id)).isEqualTo("NEW");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM outbox_events", Integer.class)).isEqualTo(1);

        publisher.publishDue(); // still inside the 1 s backoff
        verify(kafka, times(1)).send(anyString(), any(), any());

        clock.advance(Duration.ofSeconds(1));
        publisher.publishDue();
        verify(kafka, times(2)).send(anyString(), any(), any());
        assertThat(status(id)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("An event left in flight by a crashed instance is reclaimed after its lock expires")
    void reclaimsStaleInFlightEvents() {
        when(kafka.send(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        UUID id = writer.append("cart.events", "7", "CART_CLEARED", "{}");
        assertThat(repository.claim(id, clock.instant(), clock.instant().plusSeconds(60))).isTrue();

        publisher.publishDue();
        verify(kafka, never()).send(anyString(), any(), any());

        clock.advance(Duration.ofSeconds(61));
        publisher.publishDue();
        verify(kafka).send("cart.events", "7", "{}");
        assertThat(status(id)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("Only one claimer wins an event")
    void claimIsExclusive() {
        UUID id = writer.append("cart.events", "1", "CART_UPDATED", "{}");
        Instant now = clock.instant();
        assertThat(repository.claim(id, now, now.plusSeconds(60))).isTrue();
        assertThat(repository.claim(id, now, now.plusSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("Published events are deleted after the retention period")
    void purgesPublishedEventsAfterRetention() {
        when(kafka.send(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        writer.append("cart.events", "1", "CART_UPDATED", "{}");
        publisher.publishDue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(1);

        clock.advance(Duration.ofDays(8));
        publisher.publishDue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isZero();
    }

    @Test
    @DisplayName("An event written in a transaction that rolls back is never stored or sent")
    void writerJoinsTheCallersTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
        transaction.executeWithoutResult(status -> {
            writer.append("cart.events", "9", "CART_UPDATED", "{}");
            status.setRollbackOnly();
        });

        publisher.publishDue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isZero();
        verify(kafka, never()).send(anyString(), any(), any());
    }

    @Test
    @DisplayName("Backoff doubles from 1 s and is capped at 5 minutes")
    void backoffIsCapped() {
        assertThat(OutboxPublisher.backoff(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OutboxPublisher.backoff(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(OutboxPublisher.backoff(20)).isEqualTo(Duration.ofSeconds(300));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

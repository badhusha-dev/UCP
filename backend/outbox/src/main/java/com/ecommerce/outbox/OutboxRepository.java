package com.ecommerce.outbox;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBC access to {@code outbox_events}. The SQL is portable between PostgreSQL and H2. Claiming uses a conditional
 * update, so two publisher instances never send the same event at the same time.
 */
public class OutboxRepository {

    private final JdbcTemplate jdbc;

    public OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID eventId, UUID tenantId, String topic, String key, String eventType, String payload, boolean raw,
                Instant now) {
        jdbc.update("INSERT INTO outbox_events (event_id, tenant_id, topic, message_key, event_type, payload, status, "
                        + "attempt_count, next_attempt_at, created_at, payload_format) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'NEW', 0, ?, ?, ?)",
                eventId.toString(), tenantId == null ? null : tenantId.toString(), topic, key, eventType, payload,
                ts(now), ts(now), raw ? "RAW" : "TEMPLATE");
    }

    List<OutboxEvent> findDue(Instant now, int limit) {
        return jdbc.query("SELECT event_id, topic, message_key, event_type, payload, attempt_count, payload_format "
                        + "FROM outbox_events "
                        + "WHERE (status = 'NEW' AND next_attempt_at <= ?) OR (status = 'IN_FLIGHT' AND locked_until < ?) "
                        + "ORDER BY created_at LIMIT ?",
                (rs, row) -> new OutboxEvent(UUID.fromString(rs.getString("event_id")), rs.getString("topic"),
                        rs.getString("message_key"), rs.getString("event_type"), rs.getString("payload"),
                        rs.getInt("attempt_count"), "RAW".equals(rs.getString("payload_format"))),
                ts(now), ts(now), limit);
    }

    /** Returns true only for the caller that moved the event to IN_FLIGHT. */
    boolean claim(UUID eventId, Instant now, Instant lockedUntil) {
        return jdbc.update("UPDATE outbox_events SET status = 'IN_FLIGHT', locked_until = ? WHERE event_id = ? AND "
                        + "((status = 'NEW' AND next_attempt_at <= ?) OR (status = 'IN_FLIGHT' AND locked_until < ?))",
                ts(lockedUntil), eventId.toString(), ts(now), ts(now)) == 1;
    }

    void markPublished(UUID eventId, Instant now) {
        jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published_at = ?, locked_until = NULL WHERE event_id = ?",
                ts(now), eventId.toString());
    }

    void markFailed(UUID eventId, Instant nextAttemptAt) {
        jdbc.update("UPDATE outbox_events SET status = 'NEW', attempt_count = attempt_count + 1, next_attempt_at = ?, "
                + "locked_until = NULL WHERE event_id = ?", ts(nextAttemptAt), eventId.toString());
    }

    int deletePublishedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM outbox_events WHERE status = 'PUBLISHED' AND published_at < ?", ts(cutoff));
    }

    /** Events not yet published, for health checks and dashboards. */
    public long countUnpublished() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE status <> 'PUBLISHED'", Long.class);
        return count == null ? 0 : count;
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}

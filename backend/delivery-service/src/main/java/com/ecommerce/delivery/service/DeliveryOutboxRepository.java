package com.ecommerce.delivery.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DeliveryOutboxRepository {
    private final JdbcTemplate jdbc;
    public DeliveryOutboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public List<Map<String, Object>> claimBatch() {
        return jdbc.queryForList("""
                WITH candidates AS (
                    SELECT event_id FROM delivery_outbox
                    WHERE (status='NEW' AND next_attempt_at<=CURRENT_TIMESTAMP)
                       OR (status='IN_FLIGHT' AND locked_until<CURRENT_TIMESTAMP)
                    ORDER BY created_at LIMIT 25 FOR UPDATE SKIP LOCKED
                )
                UPDATE delivery_outbox e SET status='IN_FLIGHT',locked_until=CURRENT_TIMESTAMP + interval '60 seconds'
                FROM candidates c WHERE e.event_id=c.event_id
                RETURNING e.event_id::text AS "eventId",e.tenant_id::text AS "tenantId",e.aggregate_id::text AS "aggregateId",
                          e.event_type AS "eventType",e.payload
                """);
    }

    @Transactional
    public void published(UUID eventId) {
        jdbc.update("UPDATE delivery_outbox SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,locked_until=NULL WHERE event_id=?",
                eventId);
    }

    @Transactional
    public void failed(UUID eventId) {
        jdbc.update("""
                UPDATE delivery_outbox SET status='NEW',attempt_count=attempt_count+1,
                    next_attempt_at=CURRENT_TIMESTAMP + make_interval(secs => LEAST(300, POWER(2,attempt_count)::integer)),
                    locked_until=NULL WHERE event_id=?
                """, eventId);
    }
}

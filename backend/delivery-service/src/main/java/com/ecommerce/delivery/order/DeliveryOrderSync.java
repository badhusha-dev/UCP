package com.ecommerce.delivery.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Drains the delivery_order_sync outbox into Order Service with exponential backoff; rows are written in the status-change transaction. */
@Component
public class DeliveryOrderSync {
    private static final Logger log = LoggerFactory.getLogger(DeliveryOrderSync.class);
    static final int MAX_ATTEMPTS = 12;
    private final JdbcTemplate jdbc;
    private final DeliveryOrderClient orders;

    public DeliveryOrderSync(JdbcTemplate jdbc, DeliveryOrderClient orders) {
        this.jdbc = jdbc;
        this.orders = orders;
    }

    @Scheduled(fixedDelayString = "${delivery.order-sync-poll-ms:5000}")
    public void scheduledDrain() {
        drain();
    }

    /** Returns the number of rows delivered to Order Service. */
    public int drain() {
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT id,delivery_id,order_id,delivery_status,attempt_count FROM delivery_order_sync
                WHERE state='NEW' AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at,id LIMIT 50
                """);
        int synced = 0;
        for (Map<String, Object> row : due) {
            UUID id = (UUID) row.get("id");
            try {
                orders.reportDeliveryStatus(((Number) row.get("order_id")).longValue(), id, (UUID) row.get("delivery_id"),
                        String.valueOf(row.get("delivery_status")));
                jdbc.update("UPDATE delivery_order_sync SET state='SYNCED',synced_at=CURRENT_TIMESTAMP,attempt_count=attempt_count+1 WHERE id=?", id);
                synced++;
            } catch (RuntimeException failure) {
                int attempts = ((Number) row.get("attempt_count")).intValue() + 1;
                String state = attempts >= MAX_ATTEMPTS ? "FAILED" : "NEW";
                long delaySeconds = Math.min(3600L, 5L << Math.min(attempts, 10));
                jdbc.update("UPDATE delivery_order_sync SET state=?,attempt_count=?,next_attempt_at=?,last_error=? WHERE id=?",
                        state, attempts, java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(delaySeconds)),
                        failure.getClass().getSimpleName(), id);
                log.warn("Order sync {} failed (attempt {}); state={}", id, attempts, state);
            }
        }
        return synced;
    }
}

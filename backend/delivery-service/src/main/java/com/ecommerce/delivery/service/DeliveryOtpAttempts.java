package com.ecommerce.delivery.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Failed code attempts must survive the rollback of the request that rejected them, otherwise the attempt limit could be
 * bypassed. They therefore commit in their own transaction.
 */
@Component
public class DeliveryOtpAttempts {
    private final JdbcTemplate jdbc;

    public DeliveryOtpAttempts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID tenantId, UUID deliveryId) {
        jdbc.update("UPDATE delivery_otp SET attempts=attempts+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND delivery_id=?", tenantId, deliveryId);
    }
}

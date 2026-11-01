package com.ecommerce.delivery.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeliveryMaintenanceJob {
    private final DeliveryService deliveries;
    private final JdbcTemplate jdbc;

    public DeliveryMaintenanceJob(DeliveryService deliveries, JdbcTemplate jdbc) {
        this.deliveries = deliveries;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${delivery.assignment-scan-ms:30000}")
    public void expireAssignmentOffers() {
        deliveries.expireAssignmentOffers();
    }

    @Scheduled(cron = "${delivery.retention-cron:0 25 3 * * *}", zone = "UTC")
    @Transactional
    public void applyRetention() {
        jdbc.update("""
                DELETE FROM delivery_location_update l
                WHERE l.recorded_at < CURRENT_TIMESTAMP - make_interval(days => COALESCE(
                    (SELECT config_value::integer FROM delivery_configuration c WHERE c.tenant_id=l.tenant_id AND c.config_key='location_retention_days'),7))
                """);
        jdbc.update("""
                UPDATE delivery_proof p SET recipient_name=NULL,evidence_reference=NULL,note=NULL
                WHERE p.captured_at < CURRENT_TIMESTAMP - make_interval(days => COALESCE(
                    (SELECT config_value::integer FROM delivery_configuration c WHERE c.tenant_id=p.tenant_id AND c.config_key='proof_retention_days'),365))
                  AND (p.recipient_name IS NOT NULL OR p.evidence_reference IS NOT NULL OR p.note IS NOT NULL)
                """);
    }
}

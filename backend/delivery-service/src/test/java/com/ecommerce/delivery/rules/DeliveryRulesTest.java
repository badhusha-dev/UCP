package com.ecommerce.delivery.rules;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryRulesTest {
    @Test
    void defaultsAreThemselvesValid() {
        assertTrue(DeliveryRules.validate(DeliveryRules.defaults()).isEmpty());
    }

    @Test
    void onlyRegisteredRulesWithTheirTypesAndRangesAreAccepted() {
        assertFalse(DeliveryRules.validate(Map.of("script", "1+1")).isEmpty());
        assertFalse(DeliveryRules.validate(Map.of("proof.otp_ttl_minutes", "30")).isEmpty());   // wrong type
        assertFalse(DeliveryRules.validate(Map.of("proof.otp_ttl_minutes", 4)).isEmpty());      // below range
        assertFalse(DeliveryRules.validate(Map.of("proof.otp_max_attempts", 11)).isEmpty());    // above range
        assertFalse(DeliveryRules.validate(Map.of("proof.accepted_methods", List.of())).isEmpty());
        assertFalse(DeliveryRules.validate(Map.of("proof.accepted_methods", List.of("OTP", "${jndi:ldap://x}"))).isEmpty());
        assertFalse(DeliveryRules.validate(Map.of("order.eligible_statuses", "PLACED")).isEmpty());
        assertFalse(DeliveryRules.validate(Map.of()).isEmpty());
        assertTrue(DeliveryRules.validate(Map.of("proof.otp_ttl_minutes", 60, "order.eligible_statuses", List.of("PLACED"))).isEmpty());
    }

    @Test
    void publishedRulesOverlayTheSafeDefaults() {
        Map<String, Object> effective = DeliveryRules.effective(Map.of("proof.otp_ttl_minutes", 15));
        assertEquals(15, DeliveryRules.intValue(effective, DeliveryRules.OTP_TTL_MINUTES));
        assertTrue(DeliveryRules.stringSetValue(effective, DeliveryRules.PROOF_ACCEPTED_METHODS).contains("OTP"));
    }
}

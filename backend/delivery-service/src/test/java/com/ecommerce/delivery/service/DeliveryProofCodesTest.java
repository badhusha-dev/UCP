package com.ecommerce.delivery.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryProofCodesTest {
    private static DeliveryProofCodes withPepper(String pepper) {
        DeliveryProofCodes codes = new DeliveryProofCodes();
        ReflectionTestUtils.setField(codes, "pepper", pepper);
        return codes;
    }

    @Test
    void codesAreSixDigitsAndVaried() {
        DeliveryProofCodes codes = withPepper("0123456789abcdef");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = codes.newCode();
            assertTrue(code.matches("[0-9]{6}"), code);
            seen.add(code);
        }
        assertTrue(seen.size() > 150);
    }

    @Test
    void hashesAreKeyedBoundToTheDeliveryAndNeverContainTheCode() {
        UUID delivery = UUID.randomUUID();
        DeliveryProofCodes codes = withPepper("0123456789abcdef");
        String hash = codes.hashCode(delivery, "123456");
        assertFalse(hash.contains("123456"));
        assertTrue(codes.matches(hash, delivery, "123456"));
        assertFalse(codes.matches(hash, delivery, "123457"));
        assertFalse(codes.matches(hash, UUID.randomUUID(), "123456"));
        assertNotEquals(hash, withPepper("another-pepper-value").hashCode(delivery, "123456"));
    }

    @Test
    void withoutASecretEverythingFailsClosed() {
        for (String pepper : new String[]{"", null, "short"}) {
            DeliveryProofCodes codes = withPepper(pepper);
            ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> codes.hashCode(UUID.randomUUID(), "123456"));
            assertEquals(503, error.getStatusCode().value());
            assertThrows(ResponseStatusException.class, () -> codes.fingerprint("x"));
        }
    }
}

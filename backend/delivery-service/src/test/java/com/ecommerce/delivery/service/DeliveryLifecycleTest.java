package com.ecommerce.delivery.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryLifecycleTest {
    @Test
    void permitsOnlyConfiguredLifecycleTransitions() {
        assertTrue(DeliveryLifecycle.allows("CREATED", "READY_FOR_DISPATCH"));
        assertTrue(DeliveryLifecycle.allows("ASSIGNED", "ARRIVED_AT_PICKUP"));
        assertTrue(DeliveryLifecycle.allows("ARRIVED_AT_DROPOFF", "DELIVERED"));
        assertFalse(DeliveryLifecycle.allows("CREATED", "DELIVERED"));
        assertFalse(DeliveryLifecycle.allows("DELIVERED", "READY_FOR_DISPATCH"));
    }

    @Test
    void requiresReasonsForExceptionAndTerminalTransitions() {
        assertTrue(DeliveryLifecycle.requiresReason("CANCELLED"));
        assertTrue(DeliveryLifecycle.requiresReason("DELIVERY_FAILED"));
        assertTrue(DeliveryLifecycle.requiresReason("RETURN_TO_ORIGIN"));
        assertFalse(DeliveryLifecycle.requiresReason("PICKED_UP"));
    }

    @Test
    void exposesAllStatesAsImmutableLifecycleKeys() {
        assertTrue(DeliveryLifecycle.states().contains("READY_FOR_DISPATCH"));
        assertTrue(DeliveryLifecycle.states().contains("RETURNED"));
        assertThrows(UnsupportedOperationException.class, () -> DeliveryLifecycle.states().add("INVENTED"));
    }
}

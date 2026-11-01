package com.ecommerce.delivery.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Exhaustive check of every source/destination pair against the intended state machine. */
class DeliveryLifecycleMatrixTest {
    private static final List<String> STATES = List.of("CREATED", "READY_FOR_DISPATCH", "ASSIGNED", "ARRIVED_AT_PICKUP", "PICKED_UP", "IN_TRANSIT",
            "ARRIVED_AT_DROPOFF", "DELIVERED", "CANCELLED", "PICKUP_FAILED", "DELIVERY_FAILED", "RETURN_TO_ORIGIN", "RETURNED");

    private static final Map<String, Set<String>> EXPECTED = Map.ofEntries(
            Map.entry("CREATED", Set.of("READY_FOR_DISPATCH", "CANCELLED")),
            Map.entry("READY_FOR_DISPATCH", Set.of("ASSIGNED", "CANCELLED")),
            Map.entry("ASSIGNED", Set.of("READY_FOR_DISPATCH", "ARRIVED_AT_PICKUP", "PICKUP_FAILED", "CANCELLED")),
            Map.entry("ARRIVED_AT_PICKUP", Set.of("PICKED_UP", "PICKUP_FAILED")),
            Map.entry("PICKED_UP", Set.of("IN_TRANSIT", "DELIVERY_FAILED", "RETURN_TO_ORIGIN")),
            Map.entry("IN_TRANSIT", Set.of("ARRIVED_AT_DROPOFF", "DELIVERY_FAILED", "RETURN_TO_ORIGIN")),
            Map.entry("ARRIVED_AT_DROPOFF", Set.of("DELIVERED", "DELIVERY_FAILED", "RETURN_TO_ORIGIN")),
            Map.entry("DELIVERED", Set.of()),
            Map.entry("CANCELLED", Set.of()),
            Map.entry("PICKUP_FAILED", Set.of("READY_FOR_DISPATCH", "CANCELLED")),
            Map.entry("DELIVERY_FAILED", Set.of("READY_FOR_DISPATCH", "RETURN_TO_ORIGIN", "CANCELLED")),
            Map.entry("RETURN_TO_ORIGIN", Set.of("RETURNED")),
            Map.entry("RETURNED", Set.of()));

    @Test
    void everySourceDestinationPairMatchesTheStateMachine() {
        assertEquals(Set.copyOf(STATES), DeliveryLifecycle.states());
        for (String from : STATES) {
            for (String to : STATES) {
                assertEquals(EXPECTED.get(from).contains(to), DeliveryLifecycle.allows(from, to), from + " -> " + to);
            }
        }
    }

    @Test
    void terminalStatesHaveNoExitsAndUnknownStatesAreRejected() {
        for (String terminal : List.of("DELIVERED", "CANCELLED", "RETURNED")) {
            for (String to : STATES) assertFalse(DeliveryLifecycle.allows(terminal, to), terminal + " -> " + to);
        }
        assertFalse(DeliveryLifecycle.allows("TELEPORTED", "DELIVERED"));
        assertFalse(DeliveryLifecycle.allows("CREATED", "TELEPORTED"));
        assertFalse(DeliveryLifecycle.allows(null, "CREATED"));
    }

    @Test
    void partnersOwnFieldEventsAndDispatchersOwnOperationalDecisions() {
        for (String to : List.of("ARRIVED_AT_PICKUP", "PICKED_UP", "IN_TRANSIT", "ARRIVED_AT_DROPOFF", "DELIVERED")) {
            assertTrue(DeliveryLifecycle.actorMayTarget(true, to), "partner " + to);
            assertFalse(DeliveryLifecycle.actorMayTarget(false, to), "ops " + to);
        }
        for (String to : List.of("CANCELLED", "READY_FOR_DISPATCH", "RETURNED")) {
            assertTrue(DeliveryLifecycle.actorMayTarget(false, to), "ops " + to);
            assertFalse(DeliveryLifecycle.actorMayTarget(true, to), "partner " + to);
        }
        // ASSIGNED and CREATED can never be requested through the transition API by anybody.
        for (boolean partner : List.of(true, false)) {
            assertFalse(DeliveryLifecycle.actorMayTarget(partner, "ASSIGNED"));
            assertFalse(DeliveryLifecycle.actorMayTarget(partner, "CREATED"));
        }
    }
}

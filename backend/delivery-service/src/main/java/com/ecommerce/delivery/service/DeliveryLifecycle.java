package com.ecommerce.delivery.service;

import java.util.Map;
import java.util.Set;

public final class DeliveryLifecycle {
    private DeliveryLifecycle() {}

    private static final Map<String, Set<String>> NEXT = Map.ofEntries(
            Map.entry("CREATED", Set.of("READY_FOR_DISPATCH", "CANCELLED")),
            Map.entry("READY_FOR_DISPATCH", Set.of("ASSIGNED", "CANCELLED")),
            Map.entry("ASSIGNED", Set.of("READY_FOR_DISPATCH", "ARRIVED_AT_PICKUP", "PICKUP_FAILED", "CANCELLED")),
            Map.entry("ARRIVED_AT_PICKUP", Set.of("PICKED_UP", "PICKUP_FAILED")),
            Map.entry("PICKED_UP", Set.of("IN_TRANSIT", "DELIVERY_FAILED", "RETURN_TO_ORIGIN")),
            Map.entry("IN_TRANSIT", Set.of("ARRIVED_AT_DROPOFF", "DELIVERY_FAILED", "RETURN_TO_ORIGIN")),
            Map.entry("ARRIVED_AT_DROPOFF", Set.of("DELIVERED", "DELIVERY_FAILED", "RETURN_TO_ORIGIN")),
            Map.entry("PICKUP_FAILED", Set.of("READY_FOR_DISPATCH", "CANCELLED")),
            Map.entry("DELIVERY_FAILED", Set.of("READY_FOR_DISPATCH", "RETURN_TO_ORIGIN", "CANCELLED")),
            Map.entry("RETURN_TO_ORIGIN", Set.of("RETURNED")),
            Map.entry("DELIVERED", Set.of()), Map.entry("CANCELLED", Set.of()), Map.entry("RETURNED", Set.of())
    );

    public static boolean allows(String from, String to) {
        if (from == null || to == null) return false;
        return NEXT.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<String> states() {
        return NEXT.keySet();
    }

    /** Physical, in-the-field events that only the assigned partner may report. */
    private static final Set<String> PARTNER_TARGETS = Set.of("ARRIVED_AT_PICKUP", "PICKED_UP", "IN_TRANSIT", "ARRIVED_AT_DROPOFF",
            "DELIVERED", "PICKUP_FAILED", "DELIVERY_FAILED", "RETURN_TO_ORIGIN");
    /** Operational decisions made by dispatchers and administrators. ASSIGNED is excluded: it only happens through dispatch assignment. */
    private static final Set<String> OPS_TARGETS = Set.of("READY_FOR_DISPATCH", "CANCELLED", "PICKUP_FAILED", "DELIVERY_FAILED",
            "RETURN_TO_ORIGIN", "RETURNED");

    /** Whether an actor of the given kind may request this destination state through the transition API. */
    public static boolean actorMayTarget(boolean partner, String to) {
        return (partner ? PARTNER_TARGETS : OPS_TARGETS).contains(to);
    }

    public static boolean requiresReason(String state) {
        return Set.of("CANCELLED", "PICKUP_FAILED", "DELIVERY_FAILED", "RETURN_TO_ORIGIN", "RETURNED").contains(state);
    }
}

package com.ecommerce.order.rma;

import java.util.*;

/**
 * Code-defined return lifecycle. Physical retail returns and restaurant claims have different tables: a restaurant claim never
 * enters pickup, transit or inspection. Legacy statuses written by the older returns module (REQUESTED, APPROVED, RECEIVED,
 * REFUND_PENDING, REFUNDED, REJECTED, REFUND_FAILED) are part of the same lifecycle, so existing returns stay manageable.
 */
public final class ReturnStateMachine {
    private ReturnStateMachine() {}

    public static final String RETAIL = "RETAIL_RETURN", CLAIM = "RESTAURANT_CLAIM";
    private static final Map<String, Set<String>> RETAIL_FLOW = new LinkedHashMap<>();
    private static final Map<String, Set<String>> CLAIM_FLOW = new LinkedHashMap<>();

    /** Targets set only by the owning domain: Payment (refund outcome), Delivery (physical pickup progress) or the exchange fulfillment. */
    public static final Set<String> PAYMENT_OWNED = Set.of("REFUNDED", "REFUND_FAILED");
    public static final Set<String> DELIVERY_OWNED = Set.of("PICKED_UP", "IN_TRANSIT", "PICKUP_FAILED", "RETURN_LOST");
    public static final Set<String> WORKFLOW_DRIVEN = Set.of("INSPECTION_COMPLETED", "REFUND_PROCESSING", "EXCHANGED", "PICKUP_SCHEDULED");
    public static final Set<String> TERMINAL = Set.of("CLOSED", "REJECTED", "CUSTOMER_CANCELLED");
    public static final Set<String> REASON_REQUIRED = Set.of("REJECTED", "CUSTOMER_CANCELLED", "RETURN_LOST", "PICKUP_FAILED", "INSPECTION_FAILED", "CLOSED");

    private static void retail(String from, String... to) { RETAIL_FLOW.put(from, new LinkedHashSet<>(List.of(to))); }
    private static void claim(String from, String... to) { CLAIM_FLOW.put(from, new LinkedHashSet<>(List.of(to))); }

    static {
        retail("REQUESTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "CUSTOMER_CANCELLED");
        retail("UNDER_REVIEW", "APPROVED", "REJECTED", "CUSTOMER_CANCELLED");
        retail("APPROVED", "PICKUP_SCHEDULED", "RECEIVED", "CUSTOMER_CANCELLED", "CLOSED");
        retail("PICKUP_SCHEDULED", "PICKED_UP", "PICKUP_FAILED", "CUSTOMER_CANCELLED");
        retail("PICKUP_FAILED", "PICKUP_SCHEDULED", "CUSTOMER_CANCELLED", "CLOSED");
        retail("PICKED_UP", "IN_TRANSIT", "RECEIVED", "RETURN_LOST");
        retail("IN_TRANSIT", "RECEIVED", "RETURN_LOST");
        retail("RETURN_LOST", "RECEIVED", "CLOSED");
        retail("RECEIVED", "INSPECTION_PENDING", "REFUND_PENDING", "EXCHANGE_PENDING"); // direct paths only when the policy waives inspection
        retail("INSPECTION_PENDING", "INSPECTION_COMPLETED", "INSPECTION_FAILED");
        retail("INSPECTION_FAILED", "INSPECTION_PENDING", "CLOSED");
        retail("INSPECTION_COMPLETED", "REFUND_PENDING", "EXCHANGE_PENDING", "REJECTED", "CLOSED");
        retail("REFUND_PENDING", "REFUND_PROCESSING", "CLOSED");
        retail("REFUND_PROCESSING", "REFUNDED", "REFUND_FAILED");
        retail("REFUND_FAILED", "REFUND_PROCESSING", "CLOSED");
        retail("REFUNDED", "CLOSED");
        retail("EXCHANGE_PENDING", "EXCHANGED", "REFUND_PENDING", "CLOSED");
        retail("EXCHANGED", "CLOSED");

        claim("REQUESTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "CUSTOMER_CANCELLED");
        claim("UNDER_REVIEW", "APPROVED", "REJECTED", "CUSTOMER_CANCELLED");
        claim("APPROVED", "REFUND_PENDING", "CLOSED");
        claim("REFUND_PENDING", "REFUND_PROCESSING", "CLOSED");
        claim("REFUND_PROCESSING", "REFUNDED", "REFUND_FAILED");
        claim("REFUND_FAILED", "REFUND_PROCESSING", "CLOSED");
        claim("REFUNDED", "CLOSED");
    }

    public static Map<String, Set<String>> flow(String kind) { return CLAIM.equals(kind) ? CLAIM_FLOW : RETAIL_FLOW; }

    public static Set<String> known(String kind) {
        Set<String> all = new TreeSet<>(flow(kind).keySet());
        flow(kind).values().forEach(all::addAll);
        return all;
    }

    public static Set<String> allowed(String kind, String from) { return Collections.unmodifiableSet(flow(kind).getOrDefault(from == null ? "" : from, Set.of())); }
    public static boolean canMove(String kind, String from, String to) { return allowed(kind, from).contains(to); }
    public static boolean isTerminal(String status) { return TERMINAL.contains(status); }

    /** Saved menu views (statuses a queue contains). */
    public static final Map<String, Set<String>> VIEWS;
    static {
        Map<String, Set<String>> v = new LinkedHashMap<>();
        v.put("NEW", Set.of("REQUESTED"));
        v.put("PENDING_APPROVAL", Set.of("UNDER_REVIEW", "REQUESTED"));
        v.put("APPROVED", Set.of("APPROVED"));
        v.put("REJECTED", Set.of("REJECTED"));
        v.put("PICKUP_SCHEDULED", Set.of("PICKUP_SCHEDULED", "PICKUP_FAILED"));
        v.put("IN_TRANSIT", Set.of("PICKED_UP", "IN_TRANSIT", "RETURN_LOST"));
        v.put("RECEIVED", Set.of("RECEIVED"));
        v.put("INSPECTION_PENDING", Set.of("INSPECTION_PENDING", "INSPECTION_FAILED"));
        v.put("INSPECTION_COMPLETED", Set.of("INSPECTION_COMPLETED"));
        v.put("REFUND_PENDING", Set.of("REFUND_PENDING"));
        v.put("REFUND_PROCESSING", Set.of("REFUND_PROCESSING", "REFUND_FAILED"));
        v.put("REFUND_COMPLETED", Set.of("REFUNDED", "CLOSED"));
        v.put("EXCHANGE", Set.of("EXCHANGE_PENDING", "EXCHANGED"));
        VIEWS = Collections.unmodifiableMap(v);
    }
}

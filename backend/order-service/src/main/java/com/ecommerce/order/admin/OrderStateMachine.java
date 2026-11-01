package com.ecommerce.order.admin;

import java.util.*;

/**
 * Code-defined order lifecycle. Retail and restaurant orders have separate transition tables so neither is forced through the
 * other's steps. The legacy statuses PLACED / PENDING / PENDING_INVENTORY written by older checkout code are treated as aliases of
 * CREATED / CREATED / a hold-like pre-confirmation state so existing orders stay manageable.
 */
public final class OrderStateMachine {
    private OrderStateMachine() {}

    public static final String RETAIL = "RETAIL", RESTAURANT = "RESTAURANT";

    private static final Map<String, Set<String>> RETAIL_FLOW = new LinkedHashMap<>();
    private static final Map<String, Set<String>> RESTAURANT_FLOW = new LinkedHashMap<>();

    /** Targets only another domain may set: payment/refund confirmation comes from the Payment/Accounting side, never an admin click. */
    public static final Set<String> SYSTEM_ONLY_TARGETS = Set.of("PAYMENT_CONFIRMED", "PAYMENT_FAILED", "REFUNDED", "REFUND_PENDING");
    /** Targets owned by the Delivery service whenever the order has a delivery reference. */
    public static final Set<String> DELIVERY_OWNED_TARGETS = Set.of("OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED", "PICKED_UP");
    public static final Set<String> TERMINAL = Set.of("DELIVERED", "CANCELLED", "REFUNDED", "REJECTED");
    public static final Set<String> REASON_REQUIRED = Set.of("ON_HOLD", "CANCELLED", "CANCELLATION_REQUESTED", "REJECTED", "PAYMENT_FAILED", "FULFILLMENT_FAILED",
            "DELIVERY_FAILED", "PICKUP_FAILED", "PREPARATION_DELAYED", "RETURN_REQUESTED");

    private static void retail(String from, String... to) { RETAIL_FLOW.put(from, new LinkedHashSet<>(List.of(to))); }
    private static void restaurant(String from, String... to) { RESTAURANT_FLOW.put(from, new LinkedHashSet<>(List.of(to))); }

    static {
        String[] early = {"PAYMENT_PENDING", "PAYMENT_CONFIRMED", "CONFIRMED", "ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED", "PAYMENT_FAILED"};
        retail("CREATED", early); retail("PLACED", early); retail("PENDING", early);
        retail("PENDING_INVENTORY", "CONFIRMED", "ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED", "FULFILLMENT_FAILED");
        retail("PAYMENT_PENDING", "PAYMENT_CONFIRMED", "PAYMENT_FAILED", "ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED");
        retail("PAYMENT_FAILED", "PAYMENT_PENDING", "CANCELLED");
        retail("PAYMENT_CONFIRMED", "CONFIRMED", "ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED");
        retail("CONFIRMED", "PROCESSING", "ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED");
        retail("PROCESSING", "READY_FOR_FULFILLMENT", "ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED", "FULFILLMENT_FAILED");
        retail("READY_FOR_FULFILLMENT", "PARTIALLY_FULFILLED", "FULFILLED", "SHIPPED", "ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED", "FULFILLMENT_FAILED");
        retail("PARTIALLY_FULFILLED", "PARTIALLY_FULFILLED", "FULFILLED", "SHIPPED", "ON_HOLD", "CANCELLATION_REQUESTED", "FULFILLMENT_FAILED");
        retail("FULFILLED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLATION_REQUESTED");
        retail("SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED");
        retail("OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED");
        retail("DELIVERY_FAILED", "OUT_FOR_DELIVERY", "SHIPPED", "CANCELLED");
        retail("FULFILLMENT_FAILED", "PROCESSING", "READY_FOR_FULFILLMENT", "ON_HOLD", "CANCELLED");
        retail("ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED"); // release returns to the status held from; see release()
        retail("CANCELLATION_REQUESTED", "CANCELLED"); // withdrawal returns to the prior status; see release()
        retail("CANCELLED", "REFUND_PENDING");
        retail("DELIVERED", "RETURN_REQUESTED");
        retail("RETURN_REQUESTED", "REFUND_PENDING", "DELIVERED");
        retail("REFUND_PENDING", "REFUNDED");

        restaurant("RECEIVED", "ACCEPTED", "REJECTED", "CANCELLED");
        restaurant("ACCEPTED", "PREPARING", "CANCELLED");
        restaurant("PREPARING", "READY_FOR_PICKUP", "PREPARATION_DELAYED", "CANCELLED");
        restaurant("PREPARATION_DELAYED", "PREPARING", "READY_FOR_PICKUP", "CANCELLED");
        restaurant("READY_FOR_PICKUP", "PICKED_UP", "PICKUP_FAILED", "CANCELLED");
        restaurant("PICKUP_FAILED", "READY_FOR_PICKUP", "CANCELLED");
        restaurant("PICKED_UP", "DELIVERED");
    }

    public static Map<String, Set<String>> flow(String orderType) { return RESTAURANT.equals(orderType) ? RESTAURANT_FLOW : RETAIL_FLOW; }

    public static String initialStatus(String orderType) { return RESTAURANT.equals(orderType) ? "RECEIVED" : "CREATED"; }

    public static Set<String> known(String orderType) {
        Set<String> all = new TreeSet<>(flow(orderType).keySet());
        flow(orderType).values().forEach(all::addAll);
        return all;
    }

    public static boolean isKnown(String orderType, String status) { return status != null && known(orderType).contains(status); }

    /** Transitions that are structurally valid for this order type and status (before ownership, payment or permission rules). */
    public static Set<String> allowed(String orderType, String from) {
        return Collections.unmodifiableSet(flow(orderType).getOrDefault(from == null ? "" : from, Set.of()));
    }

    public static boolean canMove(String orderType, String from, String to) { return allowed(orderType, from).contains(to); }

    public static boolean isTerminal(String status) { return TERMINAL.contains(status); }

    /** Timeline/duration bookkeeping: which timestamp column a status entry stamps. */
    public static String stampFor(String status) {
        return switch (status) {
            case "CONFIRMED", "ACCEPTED" -> "confirmed";
            case "PROCESSING", "PREPARING" -> "processing";
            case "READY_FOR_FULFILLMENT", "READY_FOR_PICKUP" -> "ready";
            case "SHIPPED", "PICKED_UP" -> "shipped";
            case "DELIVERED" -> "delivered";
            case "CANCELLED", "REJECTED" -> "cancelled";
            default -> null;
        };
    }

    /** The saved menu views. Each maps to a set of current statuses (retail and restaurant statuses in one view where they mean the same thing). */
    public static final Map<String, Set<String>> VIEWS;
    static {
        Map<String, Set<String>> v = new LinkedHashMap<>();
        v.put("NEW", Set.of("CREATED", "PLACED", "PENDING"));
        v.put("PENDING_CONFIRMATION", Set.of("PAYMENT_PENDING", "PAYMENT_CONFIRMED", "RECEIVED"));
        v.put("PROCESSING", Set.of("CONFIRMED", "PROCESSING", "ACCEPTED", "PREPARING", "PREPARATION_DELAYED", "PENDING_INVENTORY"));
        v.put("READY_FOR_FULFILLMENT", Set.of("READY_FOR_FULFILLMENT", "FULFILLED", "READY_FOR_PICKUP"));
        v.put("PARTIALLY_FULFILLED", Set.of("PARTIALLY_FULFILLED"));
        v.put("SHIPPED", Set.of("SHIPPED", "PICKED_UP"));
        v.put("OUT_FOR_DELIVERY", Set.of("OUT_FOR_DELIVERY"));
        v.put("DELIVERED", Set.of("DELIVERED"));
        v.put("CANCELLED", Set.of("CANCELLED", "REJECTED"));
        v.put("FAILED", Set.of("PAYMENT_FAILED", "FULFILLMENT_FAILED", "DELIVERY_FAILED", "PICKUP_FAILED"));
        v.put("ON_HOLD", Set.of("ON_HOLD"));
        VIEWS = Collections.unmodifiableMap(v);
    }
}

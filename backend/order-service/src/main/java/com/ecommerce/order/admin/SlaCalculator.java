package com.ecommerce.order.admin;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Pure SLA arithmetic. The phase is derived from the order's current status; thresholds are tenant configuration. */
public final class SlaCalculator {
    private SlaCalculator() {}

    public record Thresholds(int confirmMinutes, int processingMinutes, int fulfillmentMinutes, int deliveryMinutes, int refundMinutes, int restaurantAcceptMinutes) {
        public static Thresholds defaults() { return new Thresholds(30, 240, 1440, 4320, 4320, 5); }
    }

    public enum Indicator { ON_TRACK, AT_RISK, BREACHED, NONE }

    public record Result(String phase, Indicator indicator, long elapsedMinutes, int thresholdMinutes) {}

    public static String phaseOf(String orderType, String status) {
        return switch (status) {
            case "RECEIVED" -> "ACCEPTANCE";
            case "CREATED", "PLACED", "PENDING", "PAYMENT_PENDING", "PAYMENT_CONFIRMED" -> "CONFIRMATION";
            case "CONFIRMED", "PROCESSING", "ACCEPTED", "PREPARING", "PREPARATION_DELAYED", "PENDING_INVENTORY" -> "PROCESSING";
            case "READY_FOR_FULFILLMENT", "PARTIALLY_FULFILLED", "FULFILLED", "READY_FOR_PICKUP" -> "FULFILLMENT";
            case "SHIPPED", "OUT_FOR_DELIVERY", "PICKED_UP" -> "DELIVERY";
            case "REFUND_PENDING" -> "REFUND";
            default -> null; // ON_HOLD pauses the clock; terminal and failed states have no running SLA
        };
    }

    public static int thresholdFor(String phase, Thresholds t) {
        return switch (phase) {
            case "ACCEPTANCE" -> t.restaurantAcceptMinutes();
            case "CONFIRMATION" -> t.confirmMinutes();
            case "PROCESSING" -> t.processingMinutes();
            case "FULFILLMENT" -> t.fulfillmentMinutes();
            case "DELIVERY" -> t.deliveryMinutes();
            default -> t.refundMinutes();
        };
    }

    /** Elapsed time in the current phase is measured from the last status change; at 80% of the threshold the order is AT_RISK. */
    public static Result evaluate(String orderType, String status, OffsetDateTime statusChangedAt, OffsetDateTime now, Thresholds t) {
        String phase = phaseOf(orderType, status);
        if (phase == null || statusChangedAt == null) return new Result(null, Indicator.NONE, 0, 0);
        long elapsed = Math.max(0, Duration.between(statusChangedAt, now).toMinutes());
        int limit = thresholdFor(phase, t);
        Indicator indicator = elapsed >= limit ? Indicator.BREACHED : elapsed * 10 >= limit * 8L ? Indicator.AT_RISK : Indicator.ON_TRACK;
        return new Result(phase, indicator, elapsed, limit);
    }
}

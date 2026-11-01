package com.ecommerce.inventory.ops.domain;

/** available = onHand - reserved - unavailable. Incoming stock is never counted until received AND accepted. */
public final class Availability {
    private Availability() {}

    public static int available(int onHand, int reserved, int unavailable) {
        return Math.max(0, onHand - reserved - unavailable);
    }

    /** Incoming is deliberately ignored: it only becomes on-hand through accepted receiving. */
    public static int available(int onHand, int reserved, int unavailable, int incoming) {
        return available(onHand, reserved, unavailable);
    }

    public enum Status { OUT_OF_STOCK, LOW_STOCK, IN_STOCK }

    public static Status status(int available, int reorderPoint) {
        if (available <= 0) return Status.OUT_OF_STOCK;
        return available <= reorderPoint ? Status.LOW_STOCK : Status.IN_STOCK;
    }
}

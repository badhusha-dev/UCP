package com.ecommerce.inventory.ops.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum TransferState {
    DRAFT, REQUESTED, APPROVED, PICKING, DISPATCHED, IN_TRANSIT, RECEIVED, COMPLETED,
    REJECTED, CANCELLED, PARTIALLY_RECEIVED, LOST, DAMAGED;

    private static final Map<TransferState, Set<TransferState>> NEXT = Map.ofEntries(
            Map.entry(DRAFT, EnumSet.of(REQUESTED, CANCELLED)),
            Map.entry(REQUESTED, EnumSet.of(APPROVED, REJECTED, CANCELLED)),
            Map.entry(APPROVED, EnumSet.of(PICKING, CANCELLED)),
            Map.entry(PICKING, EnumSet.of(DISPATCHED, CANCELLED)),
            Map.entry(DISPATCHED, EnumSet.of(IN_TRANSIT, LOST)),
            Map.entry(IN_TRANSIT, EnumSet.of(RECEIVED, PARTIALLY_RECEIVED, LOST, DAMAGED)),
            Map.entry(PARTIALLY_RECEIVED, EnumSet.of(RECEIVED, COMPLETED, LOST, DAMAGED)),
            Map.entry(RECEIVED, EnumSet.of(COMPLETED)));

    public boolean canMoveTo(TransferState target) { return NEXT.getOrDefault(this, Set.of()).contains(target); }

    /** Source stock is reserved from approval until dispatch; destination stock only changes on receipt. */
    public boolean holdsSourceReservation() { return this == APPROVED || this == PICKING; }

    public boolean terminal() { return NEXT.getOrDefault(this, Set.of()).isEmpty(); }

    public TransferState require(TransferState target) {
        if (!canMoveTo(target)) throw new IllegalStateException("Transfer cannot move from " + this + " to " + target);
        return target;
    }
}

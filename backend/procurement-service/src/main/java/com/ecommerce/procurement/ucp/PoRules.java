package com.ecommerce.procurement.ucp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Pure PO rules: lifecycle and money/receiving maths. OVERDUE is derived (ISSUED or PARTIALLY_RECEIVED past the expected date), not stored. */
final class PoRules {
    private PoRules() {}

    enum State {
        DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, ISSUED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED;

        private static final Map<State, Set<State>> NEXT = Map.of(
                DRAFT, EnumSet.of(PENDING_APPROVAL, CANCELLED),
                PENDING_APPROVAL, EnumSet.of(APPROVED, REJECTED, CANCELLED),
                REJECTED, EnumSet.of(DRAFT, CANCELLED),
                APPROVED, EnumSet.of(ISSUED, CANCELLED),
                ISSUED, EnumSet.of(PARTIALLY_RECEIVED, RECEIVED, CANCELLED),
                PARTIALLY_RECEIVED, EnumSet.of(PARTIALLY_RECEIVED, RECEIVED));

        boolean canMoveTo(State target) { return NEXT.getOrDefault(this, Set.of()).contains(target); }
    }

    static int accepted(int received, int rejected, int damaged) {
        if (received < 0 || rejected < 0 || damaged < 0 || rejected + damaged > received)
            throw new IllegalArgumentException("Rejected plus damaged cannot exceed received and quantities must be non-negative.");
        return received - rejected - damaged;
    }

    static boolean overReceipt(int ordered, int alreadyAccepted, int newAccepted, int tolerancePercent) {
        return alreadyAccepted + newAccepted > ordered + (int) Math.floor(ordered * tolerancePercent / 100.0);
    }

    /** total = (subtotal - discount) + tax on the discounted amount + shipping. */
    static BigDecimal total(BigDecimal subtotal, BigDecimal discount, BigDecimal taxRatePercent, BigDecimal shipping) {
        BigDecimal net = subtotal.subtract(discount);
        if (net.signum() < 0) throw new IllegalArgumentException("Discount exceeds subtotal.");
        return net.add(tax(subtotal, discount, taxRatePercent)).add(shipping).setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal tax(BigDecimal subtotal, BigDecimal discount, BigDecimal taxRatePercent) {
        return subtotal.subtract(discount).multiply(taxRatePercent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }
}

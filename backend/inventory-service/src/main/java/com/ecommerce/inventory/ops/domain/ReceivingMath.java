package com.ecommerce.inventory.ops.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ReceivingMath {
    private ReceivingMath() {}

    /** Only accepted quantity increases stock. received = accepted + rejected + damaged. */
    public static int accepted(int received, int rejected, int damaged) {
        if (received < 0 || rejected < 0 || damaged < 0 || rejected + damaged > received)
            throw new IllegalArgumentException("Rejected plus damaged cannot exceed received and quantities must be non-negative.");
        return received - rejected - damaged;
    }

    /** Remaining open quantity on a PO line after accepted receipts. */
    public static int openQuantity(int ordered, int alreadyAccepted) { return Math.max(0, ordered - alreadyAccepted); }

    public static boolean overReceipt(int ordered, int alreadyAccepted, int newAccepted, int tolerancePercent) {
        int allowed = ordered + (int) Math.floor(ordered * tolerancePercent / 100.0);
        return alreadyAccepted + newAccepted > allowed;
    }

    /** PO total = sum(lines) - discount + tax(on discounted subtotal) + shipping. */
    public static BigDecimal poTotal(BigDecimal subtotal, BigDecimal discount, BigDecimal taxRatePercent, BigDecimal shipping) {
        BigDecimal net = subtotal.subtract(discount);
        if (net.signum() < 0) throw new IllegalArgumentException("Discount exceeds subtotal.");
        BigDecimal tax = net.multiply(taxRatePercent).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return net.add(tax).add(shipping).setScale(4, RoundingMode.HALF_UP);
    }
}

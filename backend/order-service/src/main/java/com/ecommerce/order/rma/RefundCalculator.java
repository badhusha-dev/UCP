package com.ecommerce.order.rma;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure refund arithmetic: item value, proportional discount and tax, delivery-fee rule, fees and the cap on what was actually paid. */
public final class RefundCalculator {
    private RefundCalculator() {}

    public record Line(long orderItemId, BigDecimal unitPrice, int quantity) {}
    public record Order(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal deliveryCharge, BigDecimal payable, BigDecimal previouslyRefunded, boolean deliveryFeeAlreadyRefunded) {}
    public record Result(BigDecimal itemsAmount, BigDecimal discountAllocation, BigDecimal taxAdjustment, BigDecimal deliveryFeeRefund, BigDecimal fees, BigDecimal eligibleAmount,
                         BigDecimal finalAmount, boolean cappedByPaid, BigDecimal refundableRemaining, List<String> notes) {}

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /**
     * @param fullReturn true when, after this return, every purchased unit has been returned (drives the FULL_RETURN_ONLY delivery-fee rule)
     * @param sellerFault true for defective / wrong / damaged-on-arrival reasons: no restocking fee and the delivery fee is refunded
     */
    public static Result calculate(Order o, List<Line> lines, boolean fullReturn, boolean sellerFault, Policy policy) {
        List<String> notes = new ArrayList<>();
        BigDecimal items = BigDecimal.ZERO;
        for (Line l : lines) {
            if (l.quantity() < 0 || l.unitPrice().signum() < 0) throw new IllegalArgumentException("Quantities and prices cannot be negative.");
            items = items.add(l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())));
        }
        items = money(items);
        BigDecimal subtotal = z(o.subtotal());
        BigDecimal discountTotal = z(o.discount()).min(subtotal);
        BigDecimal discountAlloc = subtotal.signum() > 0 ? money(discountTotal.multiply(items).divide(subtotal, 6, RoundingMode.HALF_UP)) : BigDecimal.ZERO;
        BigDecimal netItems = items.subtract(discountAlloc).max(BigDecimal.ZERO);
        BigDecimal netOrder = subtotal.subtract(discountTotal);
        BigDecimal tax = netOrder.signum() > 0 ? money(z(o.tax()).multiply(netItems).divide(netOrder, 6, RoundingMode.HALF_UP)) : BigDecimal.ZERO;

        BigDecimal delivery = BigDecimal.ZERO;
        String rule = policy.deliveryFeeTreatment();
        if (!o.deliveryFeeAlreadyRefunded() && z(o.deliveryCharge()).signum() > 0) {
            boolean refundIt = switch (rule) { case "ALWAYS" -> true; case "FULL_RETURN_ONLY" -> fullReturn || sellerFault; default -> false; };
            if (refundIt) { delivery = money(o.deliveryCharge()); notes.add(sellerFault && !fullReturn ? "Delivery fee refunded because the problem was ours." : "Delivery fee refunded under the policy rule " + rule + "."); }
            else notes.add("Delivery fee is not refunded under the policy rule " + rule + ".");
        }
        BigDecimal fees = BigDecimal.ZERO;
        if (!sellerFault && policy.restockingFeePercent().signum() > 0) {
            fees = money(netItems.multiply(policy.restockingFeePercent()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            notes.add("Restocking fee of " + policy.restockingFeePercent().stripTrailingZeros().toPlainString() + "% applied.");
        }
        BigDecimal eligible = money(netItems.add(tax).add(delivery).subtract(fees)).max(BigDecimal.ZERO);
        BigDecimal remaining = money(z(o.payable()).subtract(z(o.previouslyRefunded()))).max(BigDecimal.ZERO);
        boolean capped = eligible.compareTo(remaining) > 0;
        if (capped) notes.add("Refund is limited to the amount still unrefunded on the order.");
        return new Result(items, discountAlloc, tax, delivery, fees, eligible, capped ? remaining : eligible, capped, remaining, notes);
    }

    /** Splits a refund total over lines in proportion to their net value; the last line absorbs the rounding remainder. */
    public static Map<Long, BigDecimal> allocate(BigDecimal total, List<Line> lines) {
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        BigDecimal base = BigDecimal.ZERO;
        for (Line l : lines) base = base.add(l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())));
        BigDecimal given = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            BigDecimal share = i == lines.size() - 1 ? total.subtract(given)
                    : base.signum() == 0 ? BigDecimal.ZERO : money(total.multiply(l.unitPrice().multiply(BigDecimal.valueOf(l.quantity()))).divide(base, 6, RoundingMode.HALF_UP));
            out.put(l.orderItemId(), share);
            given = given.add(share);
        }
        return out;
    }
}

package com.ecommerce.order.rma;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Pure return-eligibility evaluation against a policy. The same code runs for customer requests, operator requests and approvals,
 * so eligibility is decided by the backend only. Nothing here reads a database; the caller supplies facts.
 */
public final class EligibilityEngine {
    private EligibilityEngine() {}

    public record Line(long orderItemId, long productId, Long categoryId, int purchasedQuantity, int committedQuantity, BigDecimal unitPrice) {}
    public record Requested(long orderItemId, int quantity) {}
    public record OrderFacts(String kind, String orderType, boolean delivered, OffsetDateTime deliveredAt, OffsetDateTime orderedAt, BigDecimal orderTotal) {}
    public record LineDecision(long orderItemId, int requestedQuantity, int maxEligibleQuantity, List<String> blockers) {}
    public record Result(boolean eligible, List<String> blockers, List<LineDecision> lines, boolean evidenceRequired, boolean sellerFault, boolean autoApprove, boolean manualReview,
                         BigDecimal estimatedAmount, List<String> notes) {}

    public static Result evaluate(Policy p, OrderFacts order, List<Line> orderLines, List<Requested> requested, String reasonCode, String reportedCondition, OffsetDateTime now) {
        List<String> blockers = new ArrayList<>(), notes = new ArrayList<>();
        boolean claim = ReturnStateMachine.CLAIM.equals(order.kind());
        Optional<Policy.Reason> reason = p.reason(reasonCode == null ? "" : reasonCode);
        if (reason.isEmpty()) blockers.add("Reason '" + reasonCode + "' is not accepted by this policy.");
        boolean sellerFault = reason.map(Policy.Reason::sellerFault).orElse(false);

        if (claim && !"RESTAURANT".equals(order.orderType())) blockers.add("Restaurant claims apply only to restaurant orders.");
        if (!claim && "RESTAURANT".equals(order.orderType())) blockers.add("Restaurant orders use claims (cancellation or refund), not physical returns.");

        boolean orderLevelReason = claim && Set.of("ORDER_REJECTED", "RESTAURANT_UNAVAILABLE", "PAYMENT_NOT_ACCEPTED").contains(reasonCode);
        if (!claim && p.requireDelivered() && !order.delivered()) blockers.add("The order has not been delivered yet.");
        if (claim && !orderLevelReason && !order.delivered()) blockers.add("This claim needs the order to have been delivered.");

        Map<Long, Line> byItem = new HashMap<>();
        orderLines.forEach(l -> byItem.put(l.orderItemId(), l));
        List<Requested> asked = requested;
        if (claim && (asked == null || asked.isEmpty())) { // an order-level claim covers every line
            asked = orderLines.stream().map(l -> new Requested(l.orderItemId(), Math.max(0, l.purchasedQuantity() - l.committedQuantity()))).filter(r -> r.quantity() > 0).toList();
        }
        if (asked == null || asked.isEmpty()) blockers.add("Select at least one item.");

        OffsetDateTime anchor = order.deliveredAt() != null ? order.deliveredAt() : order.orderedAt();
        long ageHours = anchor == null ? 0 : Math.max(0, Duration.between(anchor, now).toHours());
        if (claim && anchor != null && ageHours > p.claimWindowHours()) blockers.add("The claim window of " + p.claimWindowHours() + " hours has passed.");

        List<LineDecision> decisions = new ArrayList<>();
        BigDecimal estimate = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        int totalPurchased = 0, totalAsked = 0;
        for (Requested r : asked == null ? List.<Requested>of() : asked) {
            List<String> lb = new ArrayList<>();
            Line line = byItem.get(r.orderItemId());
            if (line == null) {
                decisions.add(new LineDecision(r.orderItemId(), r.quantity(), 0, List.of("Item is not part of this order.")));
                blockers.add("Item " + r.orderItemId() + ": it is not part of this order.");
                continue;
            }
            if (!seen.add(r.orderItemId())) lb.add("An item can appear only once.");
            if (r.quantity() <= 0) lb.add("Quantity must be at least 1.");
            int open = Math.max(0, line.purchasedQuantity() - line.committedQuantity());
            int cap = Math.max(0, (int) Math.floor(line.purchasedQuantity() * p.maxQuantityPercent() / 100.0) - line.committedQuantity());
            int max = Math.min(open, cap);
            if (r.quantity() > max) lb.add("Only " + max + " of " + line.purchasedQuantity() + " units can still be " + (claim ? "claimed" : "returned") + ".");
            if (!claim) {
                if (p.nonReturnableProducts().contains(line.productId())) lb.add("This product cannot be returned.");
                if (line.categoryId() != null && p.nonReturnableCategories().contains(line.categoryId())) lb.add("Products in this category cannot be returned.");
                if (p.finalSaleProducts().contains(line.productId())) lb.add("This is a final-sale product.");
                if (line.categoryId() == null && !p.nonReturnableCategories().isEmpty() && !p.categoryWindowDays().isEmpty()) notes.add("Category of item " + line.orderItemId() + " could not be confirmed; category rules were not applied.");
                int window = p.productWindowDays().getOrDefault(line.productId(), line.categoryId() != null ? p.categoryWindowDays().getOrDefault(line.categoryId(), p.returnWindowDays()) : p.returnWindowDays());
                if (anchor != null && Duration.between(anchor, now).toDays() > window) lb.add("The return window of " + window + " days has passed.");
            }
            decisions.add(new LineDecision(r.orderItemId(), r.quantity(), max, lb));
            blockers.addAll(lb.stream().map(s -> "Item " + line.orderItemId() + ": " + s).toList());
            totalPurchased += line.purchasedQuantity() - line.committedQuantity();
            totalAsked += Math.max(0, r.quantity());
            estimate = estimate.add(line.unitPrice().multiply(BigDecimal.valueOf(Math.max(0, Math.min(r.quantity(), max)))));
        }
        if (!claim && !p.allowPartial() && totalAsked < totalPurchased) blockers.add("Partial returns are not allowed by this policy: return every remaining item.");

        String cond = reportedCondition == null ? "" : reportedCondition.toUpperCase(Locale.ROOT);
        if (!claim && !sellerFault) {
            if (cond.equals("USED") && !p.usedReturnable()) blockers.add("Used items cannot be returned under this policy.");
            if (cond.equals("DAMAGED") && !p.damagedReturnable()) blockers.add("Items damaged by the customer cannot be returned under this policy.");
        }

        boolean evidence = reason.map(Policy.Reason::evidenceRequired).orElse(false);
        boolean eligible = blockers.isEmpty();
        estimate = estimate.setScale(2, RoundingMode.HALF_UP);
        boolean manual = !eligible || (p.manualApprovalMinAmount() != null && estimate.compareTo(p.manualApprovalMinAmount()) >= 0);
        boolean auto = eligible && !manual && p.autoApproveMaxAmount() != null && estimate.compareTo(p.autoApproveMaxAmount()) <= 0 && !evidence;
        if (eligible && evidence) notes.add("Supporting evidence is required before this request can be approved.");
        return new Result(eligible, blockers, decisions, evidence, sellerFault, auto, manual, estimate, notes);
    }
}

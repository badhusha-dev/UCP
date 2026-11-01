package com.ecommerce.order.rma;

import com.ecommerce.order.rma.EligibilityEngine.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RmaDomainTest {
    private static final ObjectMapper M = new ObjectMapper();
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-31T12:00:00Z");
    private static final String R = ReturnStateMachine.RETAIL, C = ReturnStateMachine.CLAIM;

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("the retail return happy path is allowed step by step")
    void happyPath() {
        List<String> path = List.of("REQUESTED", "UNDER_REVIEW", "APPROVED", "PICKUP_SCHEDULED", "PICKED_UP", "IN_TRANSIT", "RECEIVED", "INSPECTION_PENDING", "INSPECTION_COMPLETED",
                "REFUND_PENDING", "REFUND_PROCESSING", "REFUNDED", "CLOSED");
        for (int i = 0; i + 1 < path.size(); i++) assertTrue(ReturnStateMachine.canMove(R, path.get(i), path.get(i + 1)), path.get(i) + " -> " + path.get(i + 1));
    }

    @Test @DisplayName("invalid moves are refused; terminal states have no exits")
    void invalid() {
        assertFalse(ReturnStateMachine.canMove(R, "REQUESTED", "REFUNDED"));
        assertFalse(ReturnStateMachine.canMove(R, "APPROVED", "REFUND_PROCESSING"));
        assertFalse(ReturnStateMachine.canMove(R, "PICKED_UP", "APPROVED"));
        assertFalse(ReturnStateMachine.canMove(R, "REFUNDED", "REFUND_PROCESSING"));
        assertFalse(ReturnStateMachine.canMove(R, "INSPECTION_PENDING", "REFUND_PENDING"));
        for (String t : List.of("CLOSED", "REJECTED", "CUSTOMER_CANCELLED")) assertTrue(ReturnStateMachine.allowed(R, t).isEmpty(), t);
        assertTrue(ReturnStateMachine.canMove(R, "REFUND_PROCESSING", "REFUND_FAILED"));
        assertTrue(ReturnStateMachine.canMove(R, "REFUND_FAILED", "REFUND_PROCESSING")); // retry
        assertTrue(ReturnStateMachine.canMove(R, "PICKUP_FAILED", "PICKUP_SCHEDULED"));
        assertTrue(ReturnStateMachine.canMove(R, "RETURN_LOST", "RECEIVED"));
        assertTrue(ReturnStateMachine.canMove(R, "INSPECTION_COMPLETED", "EXCHANGE_PENDING"));
        assertTrue(ReturnStateMachine.canMove(R, "EXCHANGE_PENDING", "EXCHANGED"));
    }

    @Test @DisplayName("restaurant claims never enter pickup, transit or inspection")
    void claims() {
        Set<String> claimStatuses = ReturnStateMachine.known(C);
        for (String s : List.of("PICKUP_SCHEDULED", "PICKED_UP", "IN_TRANSIT", "RECEIVED", "INSPECTION_PENDING", "EXCHANGE_PENDING"))
            assertFalse(claimStatuses.contains(s), s);
        assertTrue(ReturnStateMachine.canMove(C, "APPROVED", "REFUND_PENDING"));
        assertFalse(ReturnStateMachine.canMove(C, "APPROVED", "PICKUP_SCHEDULED"));
        assertTrue(ReturnStateMachine.canMove(C, "REFUND_PROCESSING", "REFUNDED"));
    }

    @Test @DisplayName("every flow target is a known status; each queue view holds disjoint-or-intended statuses")
    void closedFlows() {
        for (String k : List.of(R, C)) ReturnStateMachine.flow(k).values().forEach(t -> assertTrue(ReturnStateMachine.known(k).containsAll(t)));
        assertTrue(ReturnStateMachine.VIEWS.get("PENDING_APPROVAL").contains("UNDER_REVIEW"));
        assertTrue(ReturnStateMachine.PAYMENT_OWNED.contains("REFUNDED"));
        assertTrue(ReturnStateMachine.DELIVERY_OWNED.contains("PICKED_UP"));
    }

    // ---- policy ------------------------------------------------------------------------------------------------------

    @Test @DisplayName("policy documents are validated data; unknown fields are dropped and bad values refused")
    void policyValidation() throws Exception {
        var ok = Policy.normalise(M, M.readTree("{\"returnWindowDays\":14,\"reasons\":[{\"code\":\"DEFECTIVE\",\"label\":\"Defective\",\"evidenceRequired\":true,\"sellerFault\":true}],\"script\":\"x\",\"autoApproveMaxAmount\":50,\"manualApprovalMinAmount\":200}"));
        assertFalse(ok.has("script"));
        Policy p = Policy.parse(M, ok);
        assertEquals(14, p.returnWindowDays());
        assertEquals(0, bd("50").compareTo(p.autoApproveMaxAmount()));
        assertTrue(p.reason("DEFECTIVE").orElseThrow().evidenceRequired());
        for (String bad : List.of("{\"returnWindowDays\":-1,\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"}]}", "{\"returnWindowDays\":\"30\",\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"}]}",
                "{\"reasons\":[]}", "{}", "{\"reasons\":[{\"code\":\"lower\",\"label\":\"x\"}]}", "{\"reasons\":[{\"code\":\"A1\",\"label\":\"<b>x</b>\"}]}",
                "{\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"},{\"code\":\"A1\",\"label\":\"y\"}]}", "{\"deliveryFeeTreatment\":\"SOMETIMES\",\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"}]}",
                "{\"restockingFeePercent\":75,\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"}]}", "{\"autoApproveMaxAmount\":300,\"manualApprovalMinAmount\":100,\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"}]}",
                "{\"nonReturnableProducts\":[-5],\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"}]}", "{\"allowPartial\":\"yes\",\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"}]}",
                "{\"productWindowDays\":{\"abc\":5},\"reasons\":[{\"code\":\"A1\",\"label\":\"x\"}]}"))
            assertThrows(ResponseStatusException.class, () -> Policy.normalise(M, M.readTree(bad)), bad);
        assertTrue(Policy.defaults(R).reason("OTHER").isPresent());
        assertTrue(Policy.defaults(C).reason("MISSING_ITEMS").orElseThrow().evidenceRequired());
    }

    // ---- eligibility ---------------------------------------------------------------------------------------------------

    private static List<Line> lines() {
        return List.of(new Line(1, 100, 7L, 4, 0, bd("25.00")), new Line(2, 200, 8L, 2, 0, bd("40.00")));
    }
    private static OrderFacts delivered(int daysAgo) {
        return new OrderFacts(R, "RETAIL", true, NOW.minusDays(daysAgo), NOW.minusDays(daysAgo + 3), bd("180.00"));
    }
    private static Result eval(Policy p, OrderFacts o, List<Line> lines, List<Requested> req, String reason, String cond) { return EligibilityEngine.evaluate(p, o, lines, req, reason, cond, NOW); }

    @Test @DisplayName("an eligible partial return is accepted and priced")
    void eligiblePartial() {
        Result r = eval(Policy.defaults(R), delivered(5), lines(), List.of(new Requested(1, 2)), "NO_LONGER_NEEDED", "NEW");
        assertTrue(r.eligible(), String.valueOf(r.blockers()));
        assertEquals(0, bd("50.00").compareTo(r.estimatedAmount()));
        assertEquals(4, r.lines().get(0).maxEligibleQuantity());
    }

    @Test @DisplayName("return window: expired, boundary, and per-product / per-category overrides")
    void window() throws Exception {
        assertFalse(eval(Policy.defaults(R), delivered(31), lines(), List.of(new Requested(1, 1)), "OTHER", null).eligible());
        assertTrue(eval(Policy.defaults(R), delivered(30), lines(), List.of(new Requested(1, 1)), "OTHER", null).eligible());
        Policy p = Policy.parse(M, Policy.normalise(M, defaultWith("{\"returnWindowDays\":30,\"productWindowDays\":{\"100\":7},\"categoryWindowDays\":{\"8\":60}}")));
        Result r = eval(p, delivered(20), lines(), List.of(new Requested(1, 1), new Requested(2, 1)), "OTHER", null);
        assertFalse(r.eligible());
        assertFalse(r.lines().get(0).blockers().isEmpty()); // product 100 has a 7-day window
        assertTrue(r.lines().get(1).blockers().isEmpty());  // category 8 has 60 days
        assertTrue(r.blockers().get(0).contains("7 days"));
    }

    private static com.fasterxml.jackson.databind.JsonNode defaultWith(String overlay) throws Exception {
        var base = Policy.defaultConfig(M, R);
        base.setAll((com.fasterxml.jackson.databind.node.ObjectNode) M.readTree(overlay));
        return base;
    }

    @Test @DisplayName("non-returnable products and categories, and final sale")
    void nonReturnable() throws Exception {
        Policy p = Policy.parse(M, Policy.normalise(M, defaultWith("{\"nonReturnableProducts\":[100],\"nonReturnableCategories\":[8]}")));
        Result r = eval(p, delivered(1), lines(), List.of(new Requested(1, 1), new Requested(2, 1)), "OTHER", null);
        assertFalse(r.eligible());
        assertTrue(r.lines().get(0).blockers().get(0).contains("cannot be returned"));
        assertTrue(r.lines().get(1).blockers().get(0).contains("category"));
        Policy finalSale = Policy.parse(M, Policy.normalise(M, defaultWith("{\"finalSaleProducts\":[200]}")));
        assertTrue(eval(finalSale, delivered(1), lines(), List.of(new Requested(2, 1)), "OTHER", null).blockers().get(0).contains("final-sale"));
    }

    @Test @DisplayName("quantities cannot exceed what is still returnable, including units already in other returns")
    void quantities() {
        List<Line> ls = List.of(new Line(1, 100, 7L, 4, 3, bd("25.00")));
        Result r = eval(Policy.defaults(R), delivered(1), ls, List.of(new Requested(1, 2)), "OTHER", null);
        assertFalse(r.eligible());
        assertEquals(1, r.lines().get(0).maxEligibleQuantity());
        assertTrue(eval(Policy.defaults(R), delivered(1), ls, List.of(new Requested(1, 1)), "OTHER", null).eligible());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(1, 0)), "OTHER", null).eligible());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(1, -2)), "OTHER", null).eligible());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(1, 1), new Requested(1, 1)), "OTHER", null).eligible());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(99, 1)), "OTHER", null).eligible());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(), "OTHER", null).eligible());
    }

    @Test @DisplayName("partial-return switch, max quantity percent, reasons, evidence and condition rules")
    void policyRules() throws Exception {
        Policy noPartial = Policy.parse(M, Policy.normalise(M, defaultWith("{\"allowPartial\":false}")));
        assertFalse(eval(noPartial, delivered(1), lines(), List.of(new Requested(1, 1)), "OTHER", null).eligible());
        assertTrue(eval(noPartial, delivered(1), lines(), List.of(new Requested(1, 4), new Requested(2, 2)), "OTHER", null).eligible());
        Policy half = Policy.parse(M, Policy.normalise(M, defaultWith("{\"maxQuantityPercent\":50}")));
        assertEquals(2, eval(half, delivered(1), lines(), List.of(new Requested(1, 1)), "OTHER", null).lines().get(0).maxEligibleQuantity());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(1, 1)), "NOT_A_REASON", null).eligible());
        Result evidence = eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(1, 1)), "DEFECTIVE", "DAMAGED");
        assertTrue(evidence.eligible()); // seller-fault reasons override the customer-damage rule
        assertTrue(evidence.evidenceRequired());
        assertTrue(evidence.sellerFault());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(1, 1)), "NO_LONGER_NEEDED", "USED").eligible());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(1, 1)), "NO_LONGER_NEEDED", "DAMAGED").eligible());
        Policy lenient = Policy.parse(M, Policy.normalise(M, defaultWith("{\"usedReturnable\":true}")));
        assertTrue(eval(lenient, delivered(1), lines(), List.of(new Requested(1, 1)), "NO_LONGER_NEEDED", "USED").eligible());
    }

    @Test @DisplayName("delivery is required; restaurant orders are refused for physical returns and vice versa")
    void kinds() {
        OrderFacts undelivered = new OrderFacts(R, "RETAIL", false, null, NOW.minusDays(1), bd("100"));
        assertFalse(eval(Policy.defaults(R), undelivered, lines(), List.of(new Requested(1, 1)), "OTHER", null).eligible());
        OrderFacts restaurantOrder = new OrderFacts(R, "RESTAURANT", true, NOW.minusHours(1), NOW.minusHours(2), bd("30"));
        assertTrue(eval(Policy.defaults(R), restaurantOrder, lines(), List.of(new Requested(1, 1)), "OTHER", null).blockers().get(0).contains("claims"));
        OrderFacts retailClaim = new OrderFacts(C, "RETAIL", true, NOW.minusHours(1), NOW.minusHours(2), bd("30"));
        assertTrue(eval(Policy.defaults(C), retailClaim, lines(), null, "MISSING_ITEMS", null).blockers().get(0).contains("restaurant"));
    }

    @Test @DisplayName("restaurant claims: reasons, claim window in hours, order-level reasons need no delivery")
    void claimRules() {
        OrderFacts fresh = new OrderFacts(C, "RESTAURANT", true, NOW.minusHours(5), NOW.minusHours(6), bd("30"));
        Result missing = eval(Policy.defaults(C), fresh, lines(), List.of(new Requested(1, 1)), "MISSING_ITEMS", null);
        assertTrue(missing.eligible());
        assertTrue(missing.evidenceRequired());
        assertTrue(missing.sellerFault());
        assertFalse(eval(Policy.defaults(C), new OrderFacts(C, "RESTAURANT", true, NOW.minusHours(60), NOW.minusHours(61), bd("30")), lines(), List.of(new Requested(1, 1)), "LATE_DELIVERY", null).eligible());
        OrderFacts rejected = new OrderFacts(C, "RESTAURANT", false, null, NOW.minusHours(1), bd("30"));
        Result orderLevel = eval(Policy.defaults(C), rejected, lines(), null, "ORDER_REJECTED", null);
        assertTrue(orderLevel.eligible(), String.valueOf(orderLevel.blockers()));
        assertEquals(0, bd("180.00").compareTo(orderLevel.estimatedAmount())); // whole order
        assertFalse(eval(Policy.defaults(C), rejected, lines(), List.of(new Requested(1, 1)), "MISSING_ITEMS", null).eligible()); // needs a delivered order
        assertFalse(eval(Policy.defaults(C), fresh, lines(), List.of(new Requested(1, 1)), "OTHER", null).eligible()); // not a restaurant reason
    }

    @Test @DisplayName("auto-approval and manual-review thresholds come from the policy; evidence blocks auto-approval")
    void thresholds() throws Exception {
        Policy p = Policy.parse(M, Policy.normalise(M, defaultWith("{\"autoApproveMaxAmount\":60,\"manualApprovalMinAmount\":150}")));
        assertTrue(eval(p, delivered(1), lines(), List.of(new Requested(1, 2)), "OTHER", null).autoApprove()); // 50.00
        Result mid = eval(p, delivered(1), lines(), List.of(new Requested(1, 3)), "OTHER", null);          // 75.00
        assertFalse(mid.autoApprove());
        assertFalse(mid.manualReview());
        Result high = eval(p, delivered(1), List.of(new Line(1, 100, 7L, 10, 0, bd("25.00"))), List.of(new Requested(1, 8)), "OTHER", null); // 200.00
        assertTrue(high.manualReview());
        assertFalse(high.autoApprove());
        assertFalse(eval(p, delivered(1), lines(), List.of(new Requested(1, 1)), "DEFECTIVE", null).autoApprove());
        assertFalse(eval(Policy.defaults(R), delivered(1), lines(), List.of(new Requested(1, 1)), "OTHER", null).autoApprove()); // no threshold configured
    }

    // ---- refunds -------------------------------------------------------------------------------------------------------

    private static RefundCalculator.Order order(String subtotal, String discount, String tax, String delivery, String payable, String refunded, boolean deliveryRefunded) {
        return new RefundCalculator.Order(bd(subtotal), bd(discount), bd(tax), bd(delivery), bd(payable), bd(refunded), deliveryRefunded);
    }

    @Test @DisplayName("refund: item value, proportional discount and tax, delivery rule, restocking fee")
    void refundBreakdown() throws Exception {
        // 4 x 25 + 2 x 40 = 180 subtotal, 18 discount (10%), 8.10 tax on 162 net, 10 delivery; paid 162 (+ tax/delivery recorded separately)
        var o = order("180.00", "18.00", "8.10", "10.00", "180.10", "0", false);
        var partial = RefundCalculator.calculate(o, List.of(new RefundCalculator.Line(1, bd("25.00"), 2)), false, false, Policy.defaults(R));
        assertEquals(0, bd("50.00").compareTo(partial.itemsAmount()));
        assertEquals(0, bd("5.00").compareTo(partial.discountAllocation()));
        assertEquals(0, bd("2.25").compareTo(partial.taxAdjustment())); // 8.10 * 45/162
        assertEquals(0, BigDecimal.ZERO.compareTo(partial.deliveryFeeRefund())); // partial return: fee kept
        assertEquals(0, bd("47.25").compareTo(partial.finalAmount()));
        var full = RefundCalculator.calculate(o, List.of(new RefundCalculator.Line(1, bd("25.00"), 4), new RefundCalculator.Line(2, bd("40.00"), 2)), true, false, Policy.defaults(R));
        assertEquals(0, bd("10.00").compareTo(full.deliveryFeeRefund()));
        assertEquals(0, bd("180.10").compareTo(full.finalAmount()));
        var sellerFault = RefundCalculator.calculate(o, List.of(new RefundCalculator.Line(1, bd("25.00"), 1)), false, true, Policy.defaults(R));
        assertEquals(0, bd("10.00").compareTo(sellerFault.deliveryFeeRefund())); // our fault: delivery fee comes back even on a partial return
        Policy fee = Policy.parse(M, Policy.normalise(M, defaultWith("{\"restockingFeePercent\":10,\"deliveryFeeTreatment\":\"NEVER\"}")));
        var withFee = RefundCalculator.calculate(o, List.of(new RefundCalculator.Line(1, bd("25.00"), 2)), true, false, fee);
        assertEquals(0, bd("4.50").compareTo(withFee.fees())); // 10% of 45 net
        assertEquals(0, BigDecimal.ZERO.compareTo(withFee.deliveryFeeRefund()));
        var noFeeForSellerFault = RefundCalculator.calculate(o, List.of(new RefundCalculator.Line(1, bd("25.00"), 2)), false, true, fee);
        assertEquals(0, BigDecimal.ZERO.compareTo(noFeeForSellerFault.fees()));
        Policy always = Policy.parse(M, Policy.normalise(M, defaultWith("{\"deliveryFeeTreatment\":\"ALWAYS\"}")));
        assertEquals(0, bd("10.00").compareTo(RefundCalculator.calculate(o, List.of(new RefundCalculator.Line(1, bd("25.00"), 1)), false, false, always).deliveryFeeRefund()));
        assertEquals(0, BigDecimal.ZERO.compareTo(RefundCalculator.calculate(order("180.00", "18.00", "8.10", "10.00", "180.10", "0", true), List.of(new RefundCalculator.Line(1, bd("25.00"), 1)), true, false, always).deliveryFeeRefund())); // never twice
    }

    @Test @DisplayName("refund is capped by what is still unrefunded; repeated refunds cannot exceed the order")
    void refundCap() {
        var o = order("100.00", "0", "0", "0", "100.00", "70.00", false);
        var r = RefundCalculator.calculate(o, List.of(new RefundCalculator.Line(1, bd("50.00"), 1)), false, false, Policy.defaults(R));
        assertTrue(r.cappedByPaid());
        assertEquals(0, bd("30.00").compareTo(r.finalAmount()));
        assertEquals(0, bd("30.00").compareTo(r.refundableRemaining()));
        var done = RefundCalculator.calculate(order("100.00", "0", "0", "0", "100.00", "100.00", false), List.of(new RefundCalculator.Line(1, bd("50.00"), 1)), false, false, Policy.defaults(R));
        assertEquals(0, BigDecimal.ZERO.compareTo(done.finalAmount()));
        assertThrows(IllegalArgumentException.class, () -> RefundCalculator.calculate(o, List.of(new RefundCalculator.Line(1, bd("50.00"), -1)), false, false, Policy.defaults(R)));
        assertEquals(0, BigDecimal.ZERO.compareTo(RefundCalculator.calculate(order("0", "0", "0", "0", "0", "0", false), List.of(), false, false, Policy.defaults(R)).finalAmount()));
    }

    @Test @DisplayName("allocation splits a refund over lines and the last line absorbs rounding")
    void allocation() {
        var lines = List.of(new RefundCalculator.Line(1, bd("10.00"), 1), new RefundCalculator.Line(2, bd("10.00"), 1), new RefundCalculator.Line(3, bd("10.00"), 1));
        var split = RefundCalculator.allocate(bd("10.00"), lines);
        assertEquals(0, bd("10.00").compareTo(split.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(0, bd("3.33").compareTo(split.get(1L)));
        assertEquals(0, bd("3.34").compareTo(split.get(3L)));
    }
}

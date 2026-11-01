package com.ecommerce.product.pricing;

import com.ecommerce.product.pricing.DiscountEngine.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DiscountEngineTest {
    private static final Settings POLICY = new Settings(new BigDecimal("50"), BigDecimal.ZERO, "FLAGGED_ONLY");

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static CartLine line(long product, int qty, String price) { return new CartLine(product, 0, 10L, qty, bd(price), null); }
    private static Rule product(long id) { return new Rule("TRIGGER", "INCLUDE", "PRODUCT", id); }
    private static Reward pct(String p) { return new Reward("PERCENT_OFF", bd(p), null, null, null, null); }

    private static Campaign campaign(long id, int priority, boolean stackable, List<Rule> rules, Reward reward) {
        return new Campaign(id, "C" + id, "PERCENTAGE", "PRODUCT", priority, stackable, null, null, null, false, null, rules, List.of(), List.of(reward));
    }

    private static Result calc(List<CartLine> cart, List<Campaign> campaigns) {
        return DiscountEngine.calculate(cart, new Customer("cust", Set.of(), false), campaigns, POLICY);
    }

    @Test @DisplayName("percentage discount applies only to targeted products")
    void percentage() {
        Result r = calc(List.of(line(1, 2, "50.00"), line(2, 1, "30.00")), List.of(campaign(1, 100, false, List.of(product(1)), pct("10"))));
        assertEquals(bd("130.00"), r.subtotal());
        assertEquals(bd("10.00"), r.discount());
        assertEquals(bd("120.00"), r.total());
        assertEquals(bd("0.00"), r.lines().get(1).discount());
    }

    @Test @DisplayName("fixed amount is spread proportionally and never exceeds the targeted subtotal")
    void fixedAmount() {
        Campaign c = new Campaign(1, "F", "FIXED_AMOUNT", "STORE_WIDE", 1, false, null, null, null, false, null, List.of(), List.of(),
                List.of(new Reward("AMOUNT_OFF", null, bd("500.00"), null, null, null)));
        Result r = DiscountEngine.calculate(List.of(line(1, 1, "10.00"), line(2, 1, "30.00")), Customer.anonymous(), List.of(c),
                new Settings(bd("100"), BigDecimal.ZERO, "FLAGGED_ONLY"));
        assertEquals(bd("40.00"), r.discount());
        assertEquals(bd("0.00"), r.total());
    }

    @Test @DisplayName("tenant max discount percent caps the discount per line")
    void maxDiscountPercent() {
        Result r = calc(List.of(line(1, 1, "100.00")), List.of(campaign(1, 1, false, List.of(product(1)), pct("90"))));
        assertEquals(bd("50.00"), r.discount());
        assertFalse(r.warnings().isEmpty());
    }

    @Test @DisplayName("minimum margin over cost limits the discount")
    void minimumMargin() {
        CartLine l = new CartLine(1, 0, 10L, 1, bd("100.00"), bd("80.00"));
        Result r = DiscountEngine.calculate(List.of(l), Customer.anonymous(), List.of(campaign(1, 1, false, List.of(product(1)), pct("40"))),
                new Settings(bd("90"), bd("10"), "FLAGGED_ONLY"));
        // price floor = 80 / 0.9 = 88.89 -> max discount 11.11
        assertEquals(bd("11.11"), r.discount());
    }

    @Test @DisplayName("campaign max discount amount caps the campaign")
    void campaignCap() {
        Campaign c = new Campaign(1, "Cap", "PERCENTAGE", "STORE_WIDE", 1, false, null, bd("5.00"), null, false, null, List.of(), List.of(), List.of(pct("20")));
        Result r = calc(List.of(line(1, 1, "100.00")), List.of(c));
        assertEquals(bd("5.00"), r.discount());
    }

    @Test @DisplayName("minimum order value, segment and first-order eligibility")
    void eligibility() {
        Campaign min = new Campaign(1, "Min", "PERCENTAGE", "STORE_WIDE", 1, false, bd("200.00"), null, null, false, null, List.of(), List.of(), List.of(pct("10")));
        assertEquals(bd("0.00"), calc(List.of(line(1, 1, "100.00")), List.of(min)).discount());
        assertEquals(bd("30.00"), calc(List.of(line(1, 3, "100.00")), List.of(min)).discount());

        Campaign seg = new Campaign(2, "Gold", "PERCENTAGE", "STORE_WIDE", 1, false, null, null, "GOLD", false, null, List.of(), List.of(), List.of(pct("10")));
        assertEquals(bd("0.00"), calc(List.of(line(1, 1, "100.00")), List.of(seg)).discount());
        assertEquals(bd("10.00"), DiscountEngine.calculate(List.of(line(1, 1, "100.00")), new Customer("c", Set.of("GOLD"), false), List.of(seg), POLICY).discount());

        Campaign first = new Campaign(3, "First", "PERCENTAGE", "STORE_WIDE", 1, false, null, null, null, true, null, List.of(), List.of(), List.of(pct("10")));
        assertEquals(bd("0.00"), calc(List.of(line(1, 1, "100.00")), List.of(first)).discount());
        assertEquals(bd("10.00"), DiscountEngine.calculate(List.of(line(1, 1, "100.00")), new Customer("c", Set.of(), true), List.of(first), POLICY).discount());
    }

    @Test @DisplayName("buy 2 get 1 free discounts the cheapest unit of each full set")
    void buyXGetY() {
        Campaign c = new Campaign(1, "B2G1", "BUY_X_GET_Y", "PRODUCT", 1, false, null, null, null, false, null, List.of(product(1), product(2)), List.of(),
                List.of(new Reward("BUY_X_GET_Y", null, null, 2, 1, null)));
        Result r = DiscountEngine.calculate(List.of(line(1, 2, "20.00"), line(2, 1, "10.00")), Customer.anonymous(), List.of(c), new Settings(bd("100"), BigDecimal.ZERO, "FLAGGED_ONLY"));
        assertEquals(bd("10.00"), r.discount());
        Result none = calc(List.of(line(1, 2, "20.00")), List.of(c));
        assertEquals(bd("0.00"), none.discount());
    }

    @Test @DisplayName("bundle price discounts the difference per complete bundle")
    void bundle() {
        Campaign c = new Campaign(1, "Bundle", "BUNDLE", "PRODUCT", 1, false, null, null, null, false, null, List.of(product(1), product(2)), List.of(),
                List.of(new Reward("BUNDLE_PRICE", null, bd("40.00"), null, null, null)));
        assertEquals(bd("20.00"), calc(List.of(line(1, 2, "30.00"), line(2, 1, "30.00")), List.of(c)).discount());
        assertEquals(bd("0.00"), calc(List.of(line(1, 2, "30.00")), List.of(c)).discount());
    }

    @Test @DisplayName("quantity tiers pick the highest reached tier")
    void tiers() {
        Campaign c = new Campaign(1, "Tier", "QUANTITY_TIER", "PRODUCT", 1, false, null, null, null, false, null, List.of(product(1)), List.of(),
                List.of(new Reward("TIER_PERCENT", bd("5"), null, null, null, 2), new Reward("TIER_PERCENT", bd("10"), null, null, null, 5)));
        assertEquals(bd("0.00"), calc(List.of(line(1, 1, "10.00")), List.of(c)).discount());
        assertEquals(bd("1.00"), calc(List.of(line(1, 2, "10.00")), List.of(c)).discount());
        assertEquals(bd("5.00"), calc(List.of(line(1, 5, "10.00")), List.of(c)).discount());
    }

    @Test @DisplayName("stacking: non-stackable top priority wins alone; flagged campaigns stack on remaining amount")
    void stacking() {
        Campaign high = campaign(1, 200, false, List.of(product(1)), pct("10"));
        Campaign low = campaign(2, 100, true, List.of(product(1)), pct("10"));
        assertEquals(bd("10.00"), calc(List.of(line(1, 1, "100.00")), List.of(low, high)).discount());

        Campaign a = campaign(3, 200, true, List.of(product(1)), pct("10"));
        Result stacked = calc(List.of(line(1, 1, "100.00")), List.of(low, a));
        assertEquals(bd("19.00"), stacked.discount()); // 10 + 10% of the remaining 90
        assertEquals(2, stacked.applied().size());

        Result noStack = DiscountEngine.calculate(List.of(line(1, 1, "100.00")), Customer.anonymous(), List.of(low, a), new Settings(bd("90"), BigDecimal.ZERO, "NO_STACKING"));
        assertEquals(bd("10.00"), noStack.discount());
        Result all = DiscountEngine.calculate(List.of(line(1, 1, "100.00")), Customer.anonymous(), List.of(high, low), new Settings(bd("90"), BigDecimal.ZERO, "STACK_ALL"));
        assertEquals(bd("19.00"), all.discount());
    }

    @Test @DisplayName("product minimum selling price and own max discount percent limit the discount")
    void productGuardRails() {
        CartLine l = new CartLine(1, 0, 10L, 1, bd("100.00"), null, bd("90.00"), bd("20"));
        Result r = DiscountEngine.calculate(List.of(l), Customer.anonymous(), List.of(campaign(1, 1, false, List.of(product(1)), pct("50"))),
                new Settings(bd("90"), BigDecimal.ZERO, "FLAGGED_ONLY"));
        assertEquals(bd("10.00"), r.discount()); // min selling price 90 is stricter than 20%
    }

    @Test @DisplayName("invalid cart lines are rejected")
    void invalidLines() {
        assertThrows(IllegalArgumentException.class, () -> calc(List.of(line(1, 0, "10.00")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> calc(List.of(line(1, 1, "-5.00")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> calc(List.of(line(1, 1, "0")), List.of()));
    }

    @Test @DisplayName("excluded products never receive a store-wide discount")
    void exclusions() {
        Campaign c = new Campaign(1, "All but 2", "PERCENTAGE", "STORE_WIDE", 1, false, null, null, null, false, null,
                List.of(new Rule("TRIGGER", "EXCLUDE", "PRODUCT", 2)), List.of(), List.of(pct("10")));
        Result r = calc(List.of(line(1, 1, "100.00"), line(2, 1, "100.00")), List.of(c));
        assertEquals(bd("10.00"), r.discount());
        assertEquals(bd("0.00"), r.lines().get(1).discount());
    }
}

package com.ecommerce.product.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure, side-effect-free discount calculation. All inputs come from validated DB rows; there is no expression evaluation.
 * Campaigns are applied by descending priority; stacking, per-campaign caps, the tenant max-discount percentage and the
 * minimum-margin floor (when a cost price is known) are enforced here so every caller gets identical results.
 */
public final class DiscountEngine {
    private DiscountEngine() {}

    public record CartLine(long productId, long variantId, Long categoryId, int quantity, BigDecimal unitPrice, BigDecimal costPrice,
                           BigDecimal minSellingPrice, BigDecimal maxDiscountPercent) {
        public CartLine(long productId, long variantId, Long categoryId, int quantity, BigDecimal unitPrice, BigDecimal costPrice) {
            this(productId, variantId, categoryId, quantity, unitPrice, costPrice, null, null);
        }
    }
    public record Customer(String id, Set<String> segments, boolean firstOrder) {
        public static Customer anonymous() { return new Customer(null, Set.of(), false); }
    }
    public record Rule(String role, String effect, String targetType, long targetId) {}
    public record Condition(String type, BigDecimal numeric, String text) {}
    public record Reward(String type, BigDecimal percent, BigDecimal amount, Integer buyQuantity, Integer getQuantity, Integer minQuantity) {}
    public record Campaign(long id, String name, String type, String scope, int priority, boolean stackable,
                           BigDecimal minOrderValue, BigDecimal maxDiscountAmount, String segment, boolean firstOrderOnly,
                           String couponCode, List<Rule> rules, List<Condition> conditions, List<Reward> rewards) {}
    public record Settings(BigDecimal maxDiscountPercent, BigDecimal minMarginPercent, String stackingPolicy) {}

    public record Applied(long campaignId, String name, BigDecimal amount, String couponCode) {}
    public record LineResult(long productId, long variantId, int quantity, BigDecimal unitPrice, BigDecimal lineSubtotal,
                             BigDecimal discount, BigDecimal lineTotal) {}
    public record Result(List<LineResult> lines, List<Applied> applied, BigDecimal subtotal, BigDecimal discount,
                         BigDecimal total, List<String> warnings) {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static Result calculate(List<CartLine> cart, Customer customer, List<Campaign> campaigns, Settings settings) {
        int n = cart.size();
        BigDecimal[] subtotal = new BigDecimal[n];
        BigDecimal[] discount = new BigDecimal[n];
        BigDecimal cartSubtotal = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            CartLine l = cart.get(i);
            if (l.quantity() <= 0 || l.unitPrice() == null || l.unitPrice().signum() <= 0) {
                throw new IllegalArgumentException("Cart lines need a positive quantity and unit price.");
            }
            subtotal[i] = money(l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())));
            discount[i] = BigDecimal.ZERO.setScale(2);
            cartSubtotal = cartSubtotal.add(subtotal[i]);
        }
        List<Applied> applied = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Campaign> ordered = new ArrayList<>(campaigns);
        ordered.sort(Comparator.comparingInt(Campaign::priority).reversed().thenComparingLong(Campaign::id));

        boolean allStackable = true;
        for (Campaign c : ordered) {
            if (!eligible(c, customer, cart, cartSubtotal)) continue;
            if (!applied.isEmpty() && !mayStack(settings.stackingPolicy(), c, allStackable)) continue;

            BigDecimal[] remaining = new BigDecimal[n];
            for (int i = 0; i < n; i++) remaining[i] = subtotal[i].subtract(discount[i]);
            BigDecimal[] raw = rawDiscount(c, cart, remaining);
            BigDecimal total = sum(raw);
            if (c.maxDiscountAmount() != null && total.compareTo(c.maxDiscountAmount()) > 0) {
                raw = scale(raw, c.maxDiscountAmount(), total);
                warnings.add("Campaign '" + c.name() + "' capped at its maximum discount amount.");
            }
            BigDecimal given = BigDecimal.ZERO.setScale(2);
            for (int i = 0; i < n; i++) {
                if (raw[i].signum() <= 0) continue;
                BigDecimal allowed = guardrail(cart.get(i), subtotal[i], discount[i], raw[i], settings);
                if (allowed.compareTo(raw[i]) < 0) warnings.add("Discount on product " + cart.get(i).productId()
                        + " limited by the tenant max-discount / minimum-margin policy.");
                discount[i] = discount[i].add(allowed);
                given = given.add(allowed);
            }
            if (given.signum() > 0) {
                applied.add(new Applied(c.id(), c.name(), given, c.couponCode()));
                allStackable &= c.stackable();
            }
        }

        List<LineResult> lines = new ArrayList<>();
        BigDecimal totalDiscount = BigDecimal.ZERO.setScale(2);
        for (int i = 0; i < n; i++) {
            CartLine l = cart.get(i);
            lines.add(new LineResult(l.productId(), l.variantId(), l.quantity(), money(l.unitPrice()), subtotal[i],
                    discount[i], subtotal[i].subtract(discount[i])));
            totalDiscount = totalDiscount.add(discount[i]);
        }
        return new Result(lines, applied, cartSubtotal, totalDiscount, cartSubtotal.subtract(totalDiscount), warnings);
    }

    private static boolean mayStack(String policy, Campaign next, boolean allAppliedStackable) {
        return switch (policy) {
            case "STACK_ALL" -> true;
            case "FLAGGED_ONLY" -> next.stackable() && allAppliedStackable;
            default -> false;
        };
    }

    static boolean eligible(Campaign c, Customer customer, List<CartLine> cart, BigDecimal cartSubtotal) {
        if (c.segment() != null && (customer == null || !customer.segments().contains(c.segment()))) return false;
        if (c.firstOrderOnly() && (customer == null || !customer.firstOrder())) return false;
        if (c.minOrderValue() != null && cartSubtotal.compareTo(c.minOrderValue()) < 0) return false;
        for (Condition cond : c.conditions()) {
            switch (cond.type()) {
                case "MIN_ORDER_VALUE" -> { if (cond.numeric() != null && cartSubtotal.compareTo(cond.numeric()) < 0) return false; }
                case "MIN_QUANTITY" -> {
                    int qty = 0;
                    for (int i = 0; i < cart.size(); i++) if (matches(c, cart.get(i), "TRIGGER")) qty += cart.get(i).quantity();
                    if (cond.numeric() != null && qty < cond.numeric().intValue()) return false;
                }
                case "CUSTOMER_SEGMENT" -> { if (customer == null || !customer.segments().contains(cond.text())) return false; }
                case "FIRST_ORDER" -> { if (customer == null || !customer.firstOrder()) return false; }
                default -> { return false; }
            }
        }
        return true;
    }

    static boolean matches(Campaign c, CartLine line, String role) {
        List<Rule> roleRules = c.rules().stream().filter(r -> r.role().equals(role)).toList();
        for (Rule r : roleRules) if (r.effect().equals("EXCLUDE") && ruleHit(r, line)) return false;
        List<Rule> includes = roleRules.stream().filter(r -> r.effect().equals("INCLUDE")).toList();
        if (c.scope().equals("STORE_WIDE") && includes.isEmpty()) return true;
        if (includes.isEmpty()) return role.equals("REWARD") && matches(c, line, "TRIGGER");
        return includes.stream().anyMatch(r -> ruleHit(r, line));
    }

    private static boolean ruleHit(Rule r, CartLine line) {
        return switch (r.targetType()) {
            case "ALL" -> true;
            case "PRODUCT" -> r.targetId() == line.productId();
            case "CATEGORY" -> line.categoryId() != null && r.targetId() == line.categoryId();
            default -> false;
        };
    }

    private static BigDecimal[] rawDiscount(Campaign c, List<CartLine> cart, BigDecimal[] remaining) {
        int n = cart.size();
        BigDecimal[] out = zeros(n);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) if (matches(c, cart.get(i), "TRIGGER")) idx.add(i);
        if (idx.isEmpty()) return out;
        int matchedQty = idx.stream().mapToInt(i -> cart.get(i).quantity()).sum();
        BigDecimal base = idx.stream().map(i -> remaining[i]).reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Reward r : c.rewards()) {
            switch (r.type()) {
                case "PERCENT_OFF" -> { for (int i : idx) out[i] = out[i].add(pct(remaining[i], r.percent())); }
                case "AMOUNT_OFF" -> {
                    BigDecimal amt = r.amount().min(base);
                    spread(out, idx, remaining, amt);
                }
                case "TIER_PERCENT" -> { }
                case "BUY_X_GET_Y" -> buyXGetY(c, cart, remaining, out, r);
                case "BUNDLE_PRICE" -> bundle(c, cart, remaining, out, r);
                default -> { }
            }
        }
        // Quantity tiers: the highest reached tier wins.
        c.rewards().stream().filter(r -> r.type().equals("TIER_PERCENT") && r.minQuantity() != null && matchedQty >= r.minQuantity())
                .max(Comparator.comparingInt(Reward::minQuantity))
                .ifPresent(r -> { for (int i : idx) out[i] = out[i].add(pct(remaining[i], r.percent())); });
        for (int i = 0; i < n; i++) if (out[i].compareTo(remaining[i]) > 0) out[i] = remaining[i];
        return out;
    }

    private static void buyXGetY(Campaign c, List<CartLine> cart, BigDecimal[] remaining, BigDecimal[] out, Reward r) {
        int buy = r.buyQuantity() == null ? 1 : r.buyQuantity();
        int get = r.getQuantity() == null ? 1 : r.getQuantity();
        BigDecimal pct = r.percent() == null ? HUNDRED : r.percent();
        boolean sameSet = c.rules().stream().noneMatch(x -> x.role().equals("REWARD") && x.effect().equals("INCLUDE"));
        int triggerQty = 0;
        for (CartLine l : cart) if (matches(c, l, "TRIGGER")) triggerQty += l.quantity();
        List<int[]> units = new ArrayList<>(); // {lineIndex}
        for (int i = 0; i < cart.size(); i++) {
            if (matches(c, cart.get(i), sameSet ? "TRIGGER" : "REWARD")) for (int q = 0; q < cart.get(i).quantity(); q++) units.add(new int[]{i});
        }
        int rewardQty = units.size();
        int sets = sameSet ? triggerQty / (buy + get) : Math.min(triggerQty / buy, rewardQty / get);
        int freeUnits = Math.min(sets * get, rewardQty);
        units.sort(Comparator.comparing(u -> cart.get(u[0]).unitPrice()));
        for (int k = 0; k < freeUnits; k++) {
            int i = units.get(k)[0];
            BigDecimal unitDiscount = pct(cart.get(i).unitPrice(), pct);
            out[i] = out[i].add(unitDiscount).min(remaining[i]);
        }
    }

    private static void bundle(Campaign c, List<CartLine> cart, BigDecimal[] remaining, BigDecimal[] out, Reward r) {
        List<Rule> members = c.rules().stream().filter(x -> x.role().equals("TRIGGER") && x.effect().equals("INCLUDE")
                && x.targetType().equals("PRODUCT")).toList();
        if (members.isEmpty() || r.amount() == null) return;
        int bundles = Integer.MAX_VALUE;
        List<Integer> idx = new ArrayList<>();
        BigDecimal perBundleList = BigDecimal.ZERO;
        for (Rule m : members) {
            int found = -1;
            for (int i = 0; i < cart.size(); i++) if (cart.get(i).productId() == m.targetId()) found = i;
            if (found < 0) return;
            bundles = Math.min(bundles, cart.get(found).quantity());
            idx.add(found);
            perBundleList = perBundleList.add(cart.get(found).unitPrice());
        }
        BigDecimal saving = perBundleList.subtract(r.amount());
        if (saving.signum() <= 0) return;
        spread(out, idx, remaining, saving.multiply(BigDecimal.valueOf(bundles)).min(
                idx.stream().map(i -> remaining[i]).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    /** Guardrails: tenant max-discount percent of the line, and the minimum margin over cost when cost is known. */
    private static BigDecimal guardrail(CartLine line, BigDecimal lineSubtotal, BigDecimal alreadyDiscounted, BigDecimal wanted, Settings s) {
        BigDecimal maxPercent = line.maxDiscountPercent() == null ? s.maxDiscountPercent() : s.maxDiscountPercent().min(line.maxDiscountPercent());
        BigDecimal capByPercent = pct(lineSubtotal, maxPercent).subtract(alreadyDiscounted);
        BigDecimal allowed = wanted.min(capByPercent.max(BigDecimal.ZERO));
        if (line.costPrice() != null && line.costPrice().signum() > 0) {
            BigDecimal oneMinusMargin = BigDecimal.ONE.subtract(s.minMarginPercent().divide(HUNDRED, 6, RoundingMode.HALF_UP));
            BigDecimal floor = money(line.costPrice().multiply(BigDecimal.valueOf(line.quantity()))
                    .divide(oneMinusMargin, 2, RoundingMode.UP));
            BigDecimal room = lineSubtotal.subtract(alreadyDiscounted).subtract(floor);
            allowed = allowed.min(room.max(BigDecimal.ZERO));
        }
        if (line.minSellingPrice() != null) {
            BigDecimal room = lineSubtotal.subtract(alreadyDiscounted).subtract(money(line.minSellingPrice().multiply(BigDecimal.valueOf(line.quantity()))));
            allowed = allowed.min(room.max(BigDecimal.ZERO));
        }
        return money(allowed);
    }

    private static void spread(BigDecimal[] out, List<Integer> idx, BigDecimal[] remaining, BigDecimal amount) {
        BigDecimal base = idx.stream().map(i -> remaining[i]).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (base.signum() <= 0 || amount.signum() <= 0) return;
        BigDecimal given = BigDecimal.ZERO.setScale(2);
        for (int k = 0; k < idx.size(); k++) {
            int i = idx.get(k);
            BigDecimal share = k == idx.size() - 1 ? amount.subtract(given)
                    : money(amount.multiply(remaining[i]).divide(base, 4, RoundingMode.HALF_UP));
            share = share.min(remaining[i]);
            out[i] = out[i].add(share);
            given = given.add(share);
        }
    }

    private static BigDecimal[] scale(BigDecimal[] raw, BigDecimal cap, BigDecimal total) {
        BigDecimal[] out = zeros(raw.length);
        BigDecimal given = BigDecimal.ZERO.setScale(2);
        int last = -1;
        for (int i = 0; i < raw.length; i++) if (raw[i].signum() > 0) last = i;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i].signum() <= 0) continue;
            out[i] = i == last ? cap.subtract(given) : money(raw[i].multiply(cap).divide(total, 4, RoundingMode.HALF_UP));
            given = given.add(out[i]);
        }
        return out;
    }

    static BigDecimal pct(BigDecimal amount, BigDecimal percent) {
        return money(amount.multiply(percent).divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }
    static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal sum(BigDecimal[] v) { return Arrays.stream(v).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static BigDecimal[] zeros(int n) { BigDecimal[] a = new BigDecimal[n]; Arrays.fill(a, BigDecimal.ZERO.setScale(2)); return a; }
}

package com.ecommerce.order.rma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.bad;

/**
 * A return policy as validated data: numbers, booleans, enums and id lists only. There is no expression language; the engine that
 * applies it is code. Unknown fields are dropped when a policy is normalised.
 */
public record Policy(int returnWindowDays, boolean requireDelivered, boolean allowPartial, int maxQuantityPercent,
                     Set<Long> nonReturnableProducts, Set<Long> nonReturnableCategories, Set<Long> finalSaleProducts,
                     Map<Long, Integer> categoryWindowDays, Map<Long, Integer> productWindowDays,
                     boolean usedReturnable, boolean damagedReturnable, List<Reason> reasons, boolean inspectionRequired,
                     BigDecimal autoApproveMaxAmount, BigDecimal manualApprovalMinAmount, String deliveryFeeTreatment,
                     BigDecimal restockingFeePercent, boolean storeCreditAllowed, boolean exchangeAllowed, boolean exchangePriceDifferenceAllowed,
                     int claimWindowHours) {

    public record Reason(String code, String label, boolean evidenceRequired, boolean sellerFault) {}

    public static final Set<String> DELIVERY_FEE = Set.of("NEVER", "FULL_RETURN_ONLY", "ALWAYS");

    public Optional<Reason> reason(String code) { return reasons.stream().filter(r -> r.code().equals(code)).findFirst(); }

    public static Policy defaults(String kind) {
        return parse(new ObjectMapper(), defaultConfig(new ObjectMapper(), kind));
    }

    public static ObjectNode defaultConfig(ObjectMapper m, String kind) {
        ObjectNode c = m.createObjectNode();
        c.put("returnWindowDays", 30); c.put("requireDelivered", true); c.put("allowPartial", true); c.put("maxQuantityPercent", 100);
        c.putArray("nonReturnableProducts"); c.putArray("nonReturnableCategories"); c.putArray("finalSaleProducts");
        c.putObject("categoryWindowDays"); c.putObject("productWindowDays");
        c.put("usedReturnable", false); c.put("damagedReturnable", false); c.put("inspectionRequired", true);
        c.put("deliveryFeeTreatment", "FULL_RETURN_ONLY"); c.put("restockingFeePercent", 0);
        c.put("storeCreditAllowed", false); c.put("exchangeAllowed", true); c.put("exchangePriceDifferenceAllowed", true); c.put("claimWindowHours", 48);
        ArrayNode reasons = c.putArray("reasons");
        if (ReturnStateMachine.CLAIM.equals(kind)) {
            for (Object[] r : new Object[][]{{"ORDER_REJECTED", "Order rejected by the restaurant", false}, {"RESTAURANT_UNAVAILABLE", "Restaurant unavailable", false},
                    {"INCORRECT_ORDER", "Incorrect order delivered", true}, {"MISSING_ITEMS", "Items missing from the order", true}, {"DAMAGED_FOOD", "Food damaged or spoiled", true},
                    {"LATE_DELIVERY", "Delivered too late", false}, {"PAYMENT_NOT_ACCEPTED", "Payment succeeded but the order was not accepted", false}})
                reasons.addObject().put("code", (String) r[0]).put("label", (String) r[1]).put("evidenceRequired", (Boolean) r[2]).put("sellerFault", true);
        } else {
            for (Object[] r : new Object[][]{{"DEFECTIVE", "Item is defective", true, true}, {"WRONG_ITEM", "Wrong item received", true, true}, {"DAMAGED_ON_ARRIVAL", "Damaged on arrival", true, true},
                    {"NOT_AS_DESCRIBED", "Not as described", false, false}, {"NO_LONGER_NEEDED", "No longer needed", false, false}, {"SIZE_FIT", "Size or fit", false, false}, {"OTHER", "Other", false, false}})
                reasons.addObject().put("code", (String) r[0]).put("label", (String) r[1]).put("evidenceRequired", (Boolean) r[2]).put("sellerFault", (Boolean) r[3]);
        }
        return c;
    }

    /** Validates a policy document and returns its normalised form (this is what gets stored). */
    public static ObjectNode normalise(ObjectMapper m, JsonNode in) {
        if (in == null || !in.isObject()) throw bad("A policy must be a JSON object.");
        ObjectNode out = m.createObjectNode();
        out.put("returnWindowDays", intIn(in, "returnWindowDays", 0, 730, 30));
        out.put("claimWindowHours", intIn(in, "claimWindowHours", 1, 2000, 48));
        out.put("maxQuantityPercent", intIn(in, "maxQuantityPercent", 1, 100, 100));
        for (String b : List.of("requireDelivered", "allowPartial", "usedReturnable", "damagedReturnable", "inspectionRequired", "storeCreditAllowed", "exchangeAllowed", "exchangePriceDifferenceAllowed")) {
            JsonNode v = in.get(b);
            if (v != null && !v.isNull() && !v.isBoolean()) throw bad(b + " must be true or false.");
            out.put(b, v == null || v.isNull() ? DEFAULT_BOOL.get(b) : v.asBoolean());
        }
        for (String l : List.of("nonReturnableProducts", "nonReturnableCategories", "finalSaleProducts")) out.set(l, ids(m, in.get(l), l));
        for (String o : List.of("categoryWindowDays", "productWindowDays")) {
            ObjectNode target = out.putObject(o);
            JsonNode v = in.get(o);
            if (v != null && !v.isNull()) {
                if (!v.isObject()) throw bad(o + " must be an object of id → days.");
                if (v.size() > 500) throw bad(o + " is limited to 500 entries.");
                v.fields().forEachRemaining(e -> {
                    if (!e.getKey().matches("[1-9][0-9]{0,17}")) throw bad(o + " keys must be positive ids.");
                    if (!e.getValue().isInt() || e.getValue().asInt() < 0 || e.getValue().asInt() > 730) throw bad(o + " values must be 0-730 days.");
                    target.put(e.getKey(), e.getValue().asInt());
                });
            }
        }
        String fee = in.hasNonNull("deliveryFeeTreatment") ? in.get("deliveryFeeTreatment").asText() : "FULL_RETURN_ONLY";
        if (!DELIVERY_FEE.contains(fee)) throw bad("deliveryFeeTreatment must be one of " + new TreeSet<>(DELIVERY_FEE) + ".");
        out.put("deliveryFeeTreatment", fee);
        BigDecimal restock = money(in, "restockingFeePercent", BigDecimal.ZERO);
        if (restock.compareTo(new BigDecimal("50")) > 0) throw bad("restockingFeePercent cannot exceed 50.");
        out.put("restockingFeePercent", restock);
        for (String k : List.of("autoApproveMaxAmount", "manualApprovalMinAmount")) {
            if (in.hasNonNull(k)) out.put(k, money(in, k, null));
        }
        BigDecimal auto = in.hasNonNull("autoApproveMaxAmount") ? money(in, "autoApproveMaxAmount", null) : null;
        BigDecimal manual = in.hasNonNull("manualApprovalMinAmount") ? money(in, "manualApprovalMinAmount", null) : null;
        if (auto != null && manual != null && auto.compareTo(manual) > 0) throw bad("autoApproveMaxAmount cannot exceed manualApprovalMinAmount.");
        JsonNode reasons = in.get("reasons");
        ArrayNode ra = out.putArray("reasons");
        if (reasons == null || !reasons.isArray() || reasons.isEmpty()) throw bad("Define at least one return reason.");
        if (reasons.size() > 40) throw bad("At most 40 reasons.");
        Set<String> codes = new HashSet<>();
        for (JsonNode r : reasons) {
            String code = r.path("code").asText("");
            if (!code.matches("[A-Z][A-Z0-9_]{1,39}")) throw bad("Reason codes are upper-case letters, digits and '_' (2-40 chars).");
            if (!codes.add(code)) throw bad("Duplicate reason code " + code + ".");
            String label = r.path("label").asText("");
            if (label.isBlank() || label.length() > 120 || label.matches(".*[<>].*")) throw bad("Reason " + code + " needs a plain-text label of up to 120 characters.");
            ra.addObject().put("code", code).put("label", label.strip()).put("evidenceRequired", r.path("evidenceRequired").asBoolean(false)).put("sellerFault", r.path("sellerFault").asBoolean(false));
        }
        return out;
    }

    private static final Map<String, Boolean> DEFAULT_BOOL = Map.of("requireDelivered", true, "allowPartial", true, "usedReturnable", false, "damagedReturnable", false,
            "inspectionRequired", true, "storeCreditAllowed", false, "exchangeAllowed", true, "exchangePriceDifferenceAllowed", true);

    /** Reads a normalised policy document into the typed form used by the engine. */
    public static Policy parse(ObjectMapper m, JsonNode c) {
        JsonNode n = c.has("reasons") ? c : defaultConfig(m, ReturnStateMachine.RETAIL);
        Map<Long, Integer> cat = new HashMap<>(), prod = new HashMap<>();
        n.path("categoryWindowDays").fields().forEachRemaining(e -> cat.put(Long.parseLong(e.getKey()), e.getValue().asInt()));
        n.path("productWindowDays").fields().forEachRemaining(e -> prod.put(Long.parseLong(e.getKey()), e.getValue().asInt()));
        List<Reason> reasons = new ArrayList<>();
        n.path("reasons").forEach(r -> reasons.add(new Reason(r.get("code").asText(), r.get("label").asText(), r.path("evidenceRequired").asBoolean(), r.path("sellerFault").asBoolean())));
        return new Policy(n.path("returnWindowDays").asInt(30), n.path("requireDelivered").asBoolean(true), n.path("allowPartial").asBoolean(true), n.path("maxQuantityPercent").asInt(100),
                set(n.path("nonReturnableProducts")), set(n.path("nonReturnableCategories")), set(n.path("finalSaleProducts")), cat, prod,
                n.path("usedReturnable").asBoolean(false), n.path("damagedReturnable").asBoolean(false), reasons, n.path("inspectionRequired").asBoolean(true),
                n.hasNonNull("autoApproveMaxAmount") ? n.get("autoApproveMaxAmount").decimalValue() : null,
                n.hasNonNull("manualApprovalMinAmount") ? n.get("manualApprovalMinAmount").decimalValue() : null,
                n.path("deliveryFeeTreatment").asText("FULL_RETURN_ONLY"), n.path("restockingFeePercent").decimalValue(),
                n.path("storeCreditAllowed").asBoolean(false), n.path("exchangeAllowed").asBoolean(true), n.path("exchangePriceDifferenceAllowed").asBoolean(true), n.path("claimWindowHours").asInt(48));
    }

    private static Set<Long> set(JsonNode a) { Set<Long> s = new HashSet<>(); a.forEach(x -> s.add(x.asLong())); return s; }

    private static int intIn(JsonNode n, String f, int min, int max, int dflt) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return dflt;
        if (!v.isInt() || v.asInt() < min || v.asInt() > max) throw bad(f + " must be a whole number between " + min + " and " + max + ".");
        return v.asInt();
    }

    private static BigDecimal money(JsonNode n, String f, BigDecimal dflt) {
        JsonNode v = n.get(f);
        if (v == null || v.isNull()) return dflt;
        if (!v.isNumber()) throw bad(f + " must be a number.");
        BigDecimal b = v.decimalValue();
        if (b.signum() < 0 || b.compareTo(new BigDecimal("1000000000")) > 0) throw bad(f + " is out of range.");
        return b.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static ArrayNode ids(ObjectMapper m, JsonNode v, String field) {
        ArrayNode a = m.createArrayNode();
        if (v == null || v.isNull()) return a;
        if (!v.isArray() || v.size() > 2000) throw bad(field + " must be a list of at most 2000 ids.");
        Set<Long> seen = new LinkedHashSet<>();
        for (JsonNode e : v) { if (!e.canConvertToLong() || e.asLong() <= 0) throw bad(field + " must contain positive ids."); seen.add(e.asLong()); }
        seen.forEach(a::add);
        return a;
    }
}

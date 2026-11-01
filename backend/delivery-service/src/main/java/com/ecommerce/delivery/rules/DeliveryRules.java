package com.ecommerce.delivery.rules;

import java.util.*;

/**
 * The controlled registry of Delivery rules. Only the keys declared here exist; each has a fixed type and range.
 * Rules are data, never code: there is no expression language, script or class name that a tenant can supply.
 */
public final class DeliveryRules {
    private DeliveryRules() {}

    public static final String ORDER_ELIGIBLE_STATUSES = "order.eligible_statuses";
    public static final String PROOF_ACCEPTED_METHODS = "proof.accepted_methods";
    public static final String OTP_TTL_MINUTES = "proof.otp_ttl_minutes";
    public static final String OTP_MAX_ATTEMPTS = "proof.otp_max_attempts";

    private static final Set<String> ORDER_STATUSES = Set.of("PLACED", "CONFIRMED", "PROCESSING", "READY_FOR_PICKUP");
    private static final Set<String> PROOF_METHODS = Set.of("OTP", "PHOTO_REFERENCE", "SIGNATURE_REFERENCE");

    /** The safe baseline used when a tenant has published nothing, or published data cannot be read. */
    public static Map<String, Object> defaults() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put(ORDER_ELIGIBLE_STATUSES, List.of("PLACED", "CONFIRMED"));
        rules.put(PROOF_ACCEPTED_METHODS, List.of("OTP"));
        rules.put(OTP_TTL_MINUTES, 120);
        rules.put(OTP_MAX_ATTEMPTS, 5);
        return rules;
    }

    /** Returns every problem found; an empty list means the rule document is publishable. */
    public static List<String> validate(Map<String, Object> rules) {
        List<String> errors = new ArrayList<>();
        if (rules == null || rules.isEmpty()) {
            errors.add("A rule document must contain at least one rule.");
            return errors;
        }
        for (Map.Entry<String, Object> entry : rules.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case ORDER_ELIGIBLE_STATUSES -> stringSet(key, value, ORDER_STATUSES, errors);
                case PROOF_ACCEPTED_METHODS -> stringSet(key, value, PROOF_METHODS, errors);
                case OTP_TTL_MINUTES -> integerRange(key, value, 5, 1440, errors);
                case OTP_MAX_ATTEMPTS -> integerRange(key, value, 3, 10, errors);
                default -> errors.add("Unknown rule '" + key + "'. Only registered rules are accepted.");
            }
        }
        return errors;
    }

    /** Merges a validated document over the defaults. */
    public static Map<String, Object> effective(Map<String, Object> published) {
        Map<String, Object> merged = defaults();
        if (published != null) merged.putAll(published);
        return merged;
    }

    public static Set<String> stringSetValue(Map<String, Object> rules, String key) {
        Set<String> values = new LinkedHashSet<>();
        for (Object item : (Collection<?>) rules.get(key)) values.add(String.valueOf(item));
        return values;
    }

    public static int intValue(Map<String, Object> rules, String key) {
        return ((Number) rules.get(key)).intValue();
    }

    private static void stringSet(String key, Object value, Set<String> allowed, List<String> errors) {
        if (!(value instanceof Collection<?> items) || items.isEmpty()) {
            errors.add(key + " must be a non-empty list.");
            return;
        }
        for (Object item : items) {
            if (!(item instanceof String text) || !allowed.contains(text)) {
                errors.add(key + " contains an unsupported value. Allowed: " + new TreeSet<>(allowed) + ".");
                return;
            }
        }
    }

    private static void integerRange(String key, Object value, int min, int max, List<String> errors) {
        boolean whole = value instanceof Integer || value instanceof Long || value instanceof Short;
        if (!whole || ((Number) value).longValue() < min || ((Number) value).longValue() > max) {
            errors.add(key + " must be a whole number between " + min + " and " + max + ".");
        }
    }
}

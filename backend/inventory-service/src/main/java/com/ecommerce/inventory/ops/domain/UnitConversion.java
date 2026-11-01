package com.ecommerce.inventory.ops.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Validated unit conversion for ingredient inventory (e.g. kg to g). Conversions are ratios to a base unit. */
public final class UnitConversion {
    private UnitConversion() {}

    private static final Map<String, String> FAMILY = Map.of(
            "KG", "MASS", "G", "MASS", "MG", "MASS",
            "L", "VOLUME", "ML", "VOLUME",
            "EA", "COUNT", "DOZEN", "COUNT");
    private static final Map<String, BigDecimal> TO_BASE = Map.of(
            "KG", new BigDecimal("1000"), "G", BigDecimal.ONE, "MG", new BigDecimal("0.001"),
            "L", new BigDecimal("1000"), "ML", BigDecimal.ONE,
            "EA", BigDecimal.ONE, "DOZEN", new BigDecimal("12"));

    public static BigDecimal convert(BigDecimal quantity, String from, String to) {
        String f = from == null ? "" : from.toUpperCase();
        String t = to == null ? "" : to.toUpperCase();
        if (!FAMILY.containsKey(f) || !FAMILY.containsKey(t)) throw new IllegalArgumentException("Unknown unit: " + from + " -> " + to);
        if (!FAMILY.get(f).equals(FAMILY.get(t))) throw new IllegalArgumentException("Incompatible units: " + f + " and " + t);
        return quantity.multiply(TO_BASE.get(f)).divide(TO_BASE.get(t), 6, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    public static boolean isKnown(String unit) { return unit != null && FAMILY.containsKey(unit.toUpperCase()); }
}

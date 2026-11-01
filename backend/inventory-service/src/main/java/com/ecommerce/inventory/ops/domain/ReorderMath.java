package com.ecommerce.inventory.ops.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ReorderMath {
    private ReorderMath() {}

    /** reorder point = average daily demand x lead time + safety stock (rounded up). */
    public static int reorderPoint(BigDecimal avgDailyDemand, int leadTimeDays, int safetyStock) {
        if (avgDailyDemand == null || avgDailyDemand.signum() < 0 || leadTimeDays < 0 || safetyStock < 0)
            throw new IllegalArgumentException("Demand, lead time and safety stock must be non-negative.");
        return avgDailyDemand.multiply(BigDecimal.valueOf(leadTimeDays)).setScale(0, RoundingMode.CEILING).intValue() + safetyStock;
    }

    /** Suggested order quantity to bring available + incoming back to targetLevel; 0 when not below the reorder point. */
    public static int suggestedQuantity(int available, int incoming, int reorderPoint, int targetLevel, int moq) {
        if (available + incoming > reorderPoint) return 0;
        int need = Math.max(0, targetLevel - available - incoming);
        if (need == 0) return 0;
        return Math.max(need, Math.max(1, moq));
    }

    /** Days of cover at current velocity; null when there is no demand history. */
    public static BigDecimal coverageDays(int available, BigDecimal avgDailyDemand) {
        if (avgDailyDemand == null || avgDailyDemand.signum() <= 0) return null;
        return BigDecimal.valueOf(available).divide(avgDailyDemand, 1, RoundingMode.HALF_UP);
    }
}

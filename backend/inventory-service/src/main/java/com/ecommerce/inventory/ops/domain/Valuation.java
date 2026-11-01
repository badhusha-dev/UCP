package com.ecommerce.inventory.ops.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** FIFO cost layers and weighted-average cost. Money is scaled to 4 decimals. */
public final class Valuation {
    private Valuation() {}

    public record Layer(long id, int remaining, BigDecimal unitCost) {}

    public record Consumption(long layerId, int quantity, BigDecimal unitCost) {}

    /** FIFO: layers must be ordered oldest first. Throws when stock is insufficient. */
    public static List<Consumption> fifoConsume(List<Layer> layers, int quantity) {
        List<Consumption> out = new ArrayList<>();
        int need = quantity;
        for (Layer layer : layers) {
            if (need == 0) break;
            int take = Math.min(need, layer.remaining());
            if (take > 0) { out.add(new Consumption(layer.id(), take, layer.unitCost())); need -= take; }
        }
        if (need > 0) throw new IllegalStateException("Insufficient FIFO layers for quantity " + quantity);
        return out;
    }

    public static BigDecimal fifoCost(List<Consumption> consumed) {
        return consumed.stream().map(c -> c.unitCost().multiply(BigDecimal.valueOf(c.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal layersValue(List<Layer> layers) {
        return layers.stream().map(l -> l.unitCost().multiply(BigDecimal.valueOf(l.remaining()))).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
    }

    /** New weighted average after receiving addQty at addCost. */
    public static BigDecimal weightedAverage(int onHand, BigDecimal currentAvg, int addQty, BigDecimal addCost) {
        int total = onHand + addQty;
        if (total <= 0) return BigDecimal.ZERO.setScale(4);
        BigDecimal value = currentAvg.multiply(BigDecimal.valueOf(onHand)).add(addCost.multiply(BigDecimal.valueOf(addQty)));
        return value.divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }
}

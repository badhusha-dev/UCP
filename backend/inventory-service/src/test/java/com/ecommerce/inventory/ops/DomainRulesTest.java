package com.ecommerce.inventory.ops;

import com.ecommerce.inventory.ops.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DomainRulesTest {
    @Test void availabilitySubtractsReservedAndUnavailableAndIgnoresIncoming() {
        assertEquals(70, Availability.available(100, 20, 10));
        assertEquals(70, Availability.available(100, 20, 10, 500));
        assertEquals(0, Availability.available(5, 10, 0));
        assertEquals(Availability.Status.OUT_OF_STOCK, Availability.status(0, 5));
        assertEquals(Availability.Status.LOW_STOCK, Availability.status(5, 5));
        assertEquals(Availability.Status.IN_STOCK, Availability.status(6, 5));
    }

    @Test void reorderPointIsDemandTimesLeadTimePlusSafety() {
        assertEquals(35, ReorderMath.reorderPoint(new BigDecimal("2.5"), 10, 10));
        assertEquals(11, ReorderMath.reorderPoint(new BigDecimal("0.3"), 3, 10)); // 0.9 rounds up to 1
        assertThrows(IllegalArgumentException.class, () -> ReorderMath.reorderPoint(new BigDecimal("-1"), 1, 1));
        assertEquals(0, ReorderMath.suggestedQuantity(50, 0, 35, 100, 1));
        assertEquals(0, ReorderMath.suggestedQuantity(20, 20, 35, 100, 1)); // incoming covers it
        assertEquals(75, ReorderMath.suggestedQuantity(20, 5, 35, 100, 1));
        assertEquals(80, ReorderMath.suggestedQuantity(20, 5, 35, 100, 80));
        assertEquals(new BigDecimal("10.0"), ReorderMath.coverageDays(20, new BigDecimal("2")));
        assertNull(ReorderMath.coverageDays(20, BigDecimal.ZERO));
    }

    @Test void unitConversionIsValidated() {
        assertEquals(0, new BigDecimal("2500").compareTo(UnitConversion.convert(new BigDecimal("2.5"), "kg", "g")));
        assertEquals(0, new BigDecimal("24").compareTo(UnitConversion.convert(new BigDecimal("2"), "DOZEN", "EA")));
        assertThrows(IllegalArgumentException.class, () -> UnitConversion.convert(BigDecimal.ONE, "KG", "L"));
        assertThrows(IllegalArgumentException.class, () -> UnitConversion.convert(BigDecimal.ONE, "KG", "BOX"));
    }

    @Test void transferStateMachine() {
        assertTrue(TransferState.DRAFT.canMoveTo(TransferState.REQUESTED));
        assertFalse(TransferState.DRAFT.canMoveTo(TransferState.DISPATCHED));
        assertFalse(TransferState.REQUESTED.canMoveTo(TransferState.PICKING));
        assertTrue(TransferState.IN_TRANSIT.canMoveTo(TransferState.PARTIALLY_RECEIVED));
        assertTrue(TransferState.COMPLETED.terminal());
        assertTrue(TransferState.APPROVED.holdsSourceReservation());
        assertFalse(TransferState.DISPATCHED.holdsSourceReservation());
        assertThrows(IllegalStateException.class, () -> TransferState.COMPLETED.require(TransferState.DRAFT));
    }

    @Test void fifoConsumesOldestLayersFirst() {
        List<Valuation.Layer> layers = List.of(new Valuation.Layer(1, 10, new BigDecimal("2.00")), new Valuation.Layer(2, 10, new BigDecimal("3.00")));
        var used = Valuation.fifoConsume(layers, 15);
        assertEquals(2, used.size());
        assertEquals(new BigDecimal("35.0000"), Valuation.fifoCost(used));
        assertEquals(new BigDecimal("50.0000"), Valuation.layersValue(layers));
        assertThrows(IllegalStateException.class, () -> Valuation.fifoConsume(layers, 21));
    }

    @Test void weightedAverageCost() {
        assertEquals(new BigDecimal("2.6667"), Valuation.weightedAverage(10, new BigDecimal("2.00"), 20, new BigDecimal("3.00")));
        assertEquals(new BigDecimal("3.0000"), Valuation.weightedAverage(0, BigDecimal.ZERO, 5, new BigDecimal("3")));
    }

    @Test void receivingMath() {
        assertEquals(8, ReceivingMath.accepted(10, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ReceivingMath.accepted(10, 6, 5));
        assertEquals(2, ReceivingMath.openQuantity(10, 8));
        assertTrue(ReceivingMath.overReceipt(100, 100, 6, 5));
        assertFalse(ReceivingMath.overReceipt(100, 100, 5, 5));
        assertEquals(new BigDecimal("1064.0000"), ReceivingMath.poTotal(new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("18"), new BigDecimal("2")));
    }
}

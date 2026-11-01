package com.ecommerce.order.admin;

import com.ecommerce.order.admin.SlaCalculator.Indicator;
import com.ecommerce.order.admin.SlaCalculator.Thresholds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {
    private static final String R = OrderStateMachine.RETAIL, F = OrderStateMachine.RESTAURANT;

    @Test @DisplayName("the happy retail path is allowed step by step")
    void retailHappyPath() {
        List<String> path = List.of("CREATED", "PAYMENT_PENDING", "PAYMENT_CONFIRMED", "CONFIRMED", "PROCESSING", "READY_FOR_FULFILLMENT", "PARTIALLY_FULFILLED", "FULFILLED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED");
        for (int i = 0; i + 1 < path.size(); i++) assertTrue(OrderStateMachine.canMove(R, path.get(i), path.get(i + 1)), path.get(i) + " -> " + path.get(i + 1));
    }

    @Test @DisplayName("steps cannot be skipped or reversed")
    void invalidRetail() {
        assertFalse(OrderStateMachine.canMove(R, "CREATED", "SHIPPED"));
        assertFalse(OrderStateMachine.canMove(R, "PROCESSING", "CONFIRMED"));
        assertFalse(OrderStateMachine.canMove(R, "DELIVERED", "PROCESSING"));
        assertFalse(OrderStateMachine.canMove(R, "CANCELLED", "PROCESSING"));
        assertFalse(OrderStateMachine.canMove(R, "REFUNDED", "REFUND_PENDING"));
        assertFalse(OrderStateMachine.canMove(R, "SHIPPED", "CANCELLED")); // shipped goods go through returns
        assertFalse(OrderStateMachine.canMove(R, "NOPE", "CONFIRMED"));
        assertFalse(OrderStateMachine.canMove(R, null, "CONFIRMED"));
    }

    @Test @DisplayName("legacy statuses stay manageable")
    void legacyAliases() {
        assertTrue(OrderStateMachine.canMove(R, "PLACED", "CONFIRMED"));
        assertTrue(OrderStateMachine.canMove(R, "PLACED", "CANCELLED"));
        assertFalse(OrderStateMachine.canMove(R, "PLACED", "SHIPPED"));
        assertTrue(OrderStateMachine.canMove(R, "PENDING_INVENTORY", "CONFIRMED"));
    }

    @Test @DisplayName("exceptional states: hold, cancellation, failures and returns")
    void exceptionalStates() {
        for (String s : List.of("CREATED", "CONFIRMED", "PROCESSING", "READY_FOR_FULFILLMENT")) {
            assertTrue(OrderStateMachine.canMove(R, s, "ON_HOLD"), s);
            assertTrue(OrderStateMachine.canMove(R, s, "CANCELLATION_REQUESTED"), s);
        }
        assertTrue(OrderStateMachine.canMove(R, "PAYMENT_PENDING", "PAYMENT_FAILED"));
        assertTrue(OrderStateMachine.canMove(R, "PROCESSING", "FULFILLMENT_FAILED"));
        assertTrue(OrderStateMachine.canMove(R, "OUT_FOR_DELIVERY", "DELIVERY_FAILED"));
        assertTrue(OrderStateMachine.canMove(R, "DELIVERY_FAILED", "OUT_FOR_DELIVERY"));
        assertTrue(OrderStateMachine.canMove(R, "DELIVERED", "RETURN_REQUESTED"));
        assertTrue(OrderStateMachine.canMove(R, "RETURN_REQUESTED", "REFUND_PENDING"));
        assertTrue(OrderStateMachine.canMove(R, "REFUND_PENDING", "REFUNDED"));
        assertTrue(OrderStateMachine.canMove(R, "CANCELLED", "REFUND_PENDING"));
        assertFalse(OrderStateMachine.canMove(R, "DELIVERED", "CANCELLED"));
    }

    @Test @DisplayName("restaurant orders have their own lifecycle and never touch warehouse states")
    void restaurant() {
        List<String> path = List.of("RECEIVED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "DELIVERED");
        for (int i = 0; i + 1 < path.size(); i++) assertTrue(OrderStateMachine.canMove(F, path.get(i), path.get(i + 1)), path.get(i));
        assertTrue(OrderStateMachine.canMove(F, "RECEIVED", "REJECTED"));
        assertTrue(OrderStateMachine.canMove(F, "PREPARING", "PREPARATION_DELAYED"));
        assertTrue(OrderStateMachine.canMove(F, "READY_FOR_PICKUP", "PICKUP_FAILED"));
        assertFalse(OrderStateMachine.canMove(F, "RECEIVED", "PREPARING"));
        assertFalse(OrderStateMachine.canMove(F, "ACCEPTED", "SHIPPED"));
        assertFalse(OrderStateMachine.canMove(F, "CONFIRMED", "PROCESSING"));
        assertFalse(OrderStateMachine.canMove(R, "RECEIVED", "ACCEPTED")); // restaurant steps are not valid for retail
        assertTrue(Collections.disjoint(OrderStateMachine.known(F), Set.of("READY_FOR_FULFILLMENT", "PARTIALLY_FULFILLED", "FULFILLED", "SHIPPED", "PAYMENT_PENDING")));
        assertEquals("RECEIVED", OrderStateMachine.initialStatus(F));
        assertEquals("CREATED", OrderStateMachine.initialStatus(R));
    }

    @Test @DisplayName("every target of every flow is itself a known status, and terminal states have no exits (except refund paths)")
    void flowsAreClosed() {
        for (String type : List.of(R, F)) {
            Set<String> known = OrderStateMachine.known(type);
            OrderStateMachine.flow(type).values().forEach(targets -> assertTrue(known.containsAll(targets)));
        }
        assertTrue(OrderStateMachine.allowed(R, "REFUNDED").isEmpty());
        assertTrue(OrderStateMachine.allowed(F, "REJECTED").isEmpty());
        assertTrue(OrderStateMachine.allowed(F, "DELIVERED").isEmpty());
        assertTrue(OrderStateMachine.allowed(R, "DELIVERED").contains("RETURN_REQUESTED"));
    }

    @Test @DisplayName("menu views cover every status that needs attention exactly once")
    void views() {
        Map<String, Integer> seen = new HashMap<>();
        OrderStateMachine.VIEWS.values().forEach(set -> set.forEach(s -> seen.merge(s, 1, Integer::sum)));
        assertTrue(seen.values().stream().allMatch(n -> n == 1), "a status must not appear in two views: " + seen);
        for (String s : List.of("CREATED", "RECEIVED", "PROCESSING", "PREPARING", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED", "PAYMENT_FAILED", "ON_HOLD", "PARTIALLY_FULFILLED"))
            assertTrue(seen.containsKey(s), s + " should appear in a view");
    }

    @Test @DisplayName("SLA phases, thresholds and indicators")
    void sla() {
        Thresholds t = new Thresholds(30, 240, 1440, 4320, 4320, 5);
        OffsetDateTime now = OffsetDateTime.parse("2026-10-31T12:00:00Z");
        assertEquals(Indicator.ON_TRACK, SlaCalculator.evaluate(R, "CREATED", now.minusMinutes(10), now, t).indicator());
        assertEquals(Indicator.AT_RISK, SlaCalculator.evaluate(R, "CREATED", now.minusMinutes(24), now, t).indicator()); // 80% of 30
        assertEquals(Indicator.BREACHED, SlaCalculator.evaluate(R, "CREATED", now.minusMinutes(30), now, t).indicator());
        assertEquals("CONFIRMATION", SlaCalculator.evaluate(R, "PAYMENT_PENDING", now.minusMinutes(1), now, t).phase());
        assertEquals("ACCEPTANCE", SlaCalculator.evaluate(F, "RECEIVED", now.minusMinutes(1), now, t).phase());
        assertEquals(Indicator.BREACHED, SlaCalculator.evaluate(F, "RECEIVED", now.minusMinutes(6), now, t).indicator()); // restaurant accept = 5
        assertEquals(Indicator.BREACHED, SlaCalculator.evaluate(R, "PROCESSING", now.minusMinutes(241), now, t).indicator());
        assertEquals(Indicator.ON_TRACK, SlaCalculator.evaluate(R, "SHIPPED", now.minusMinutes(100), now, t).indicator());
        assertEquals(Indicator.BREACHED, SlaCalculator.evaluate(R, "OUT_FOR_DELIVERY", now.minusDays(4), now, t).indicator());
        assertEquals(Indicator.NONE, SlaCalculator.evaluate(R, "ON_HOLD", now.minusDays(40), now, t).indicator()); // hold pauses the clock
        assertEquals(Indicator.NONE, SlaCalculator.evaluate(R, "DELIVERED", now.minusDays(40), now, t).indicator());
        assertEquals(Indicator.NONE, SlaCalculator.evaluate(R, "CREATED", null, now, t).indicator());
        assertEquals(0, SlaCalculator.evaluate(R, "CREATED", now.plusMinutes(5), now, t).elapsedMinutes()); // clock skew never goes negative
        // per-tenant thresholds change the outcome
        assertEquals(Indicator.ON_TRACK, SlaCalculator.evaluate(R, "CREATED", now.minusMinutes(60), now, new Thresholds(240, 240, 1440, 4320, 4320, 5)).indicator());
    }

    @Test @DisplayName("CSV cells starting with formula characters are neutralised")
    void csv() {
        assertEquals("'=SUM(A1)", AdminSupport.csvCell("=SUM(A1)"));
        assertEquals("'+cmd", AdminSupport.csvCell("+cmd"));
        assertEquals("-5.00", AdminSupport.csvCell("-5.00"));
        assertEquals("\"a,b\"", AdminSupport.csvCell("a,b"));
        assertEquals(Long.valueOf(42), AdminSupport.parseOrderNumber("ORD-000042"));
        assertEquals(Long.valueOf(7), AdminSupport.parseOrderNumber("#7"));
        assertNull(AdminSupport.parseOrderNumber("bob@example.com"));
    }
}

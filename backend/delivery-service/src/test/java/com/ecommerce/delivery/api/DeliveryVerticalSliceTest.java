package com.ecommerce.delivery.api;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecommerce.delivery.order.DeliveryOrderClient;
import com.ecommerce.delivery.order.DeliveryOrderSync;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The first vertical slice, driven end to end through the HTTP API: order -> delivery -> dispatch -> pickup -> proof -> delivered -> order. */
class DeliveryVerticalSliceTest extends DeliveryApiTestSupport {
    @Autowired private DeliveryOrderSync orderSync;

    @Test
    void orderBackedDeliveryRunsTheWholeLifecycleThroughRealApis() throws Exception {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(logs);
        try {
            String partnerId = onboardPartner(tenantA, 501, 10, "Z-" + UUID.randomUUID().toString().substring(0, 8));
            JsonNode ready = readyDelivery(tenantA, 10, 7);
            String id = ready.get("id").asText();
            assertThat(ready.get("status").asText()).isEqualTo("READY_FOR_DISPATCH");
            assertThat(ready.get("recipientUserId").asLong()).isEqualTo(7);          // taken from the order, not the caller
            assertThat(new BigDecimal(ready.get("orderTotal").asText())).isEqualByComparingTo("49.90");

            JsonNode accepted = assignAndAccept(tenantA, 10, 501, ready, partnerId);
            assertThat(accepted.get("status").asText()).isEqualTo("ASSIGNED");
            String partner = partner(tenantA, 501);

            JsonNode current = accepted;
            for (String next : List.of("ARRIVED_AT_PICKUP", "PICKED_UP", "IN_TRANSIT", "ARRIVED_AT_DROPOFF")) {
                MvcResult moved = transition(tenantA, partner, current, next, null);
                assertThat(status(moved)).as(next).isEqualTo(200);
                current = body(moved);
                assertThat(current.get("status").asText()).isEqualTo(next);
            }

            // The recipient obtains the one-time code; only a hash is stored.
            MvcResult codeResult = postJson("/api/delivery/my/deliveries/" + id + "/delivery-code", recipient(tenantA, 7), Map.of());
            assertThat(status(codeResult)).isEqualTo(200);
            assertThat(codeResult.getResponse().getHeader("Cache-Control")).contains("no-store");
            String code = body(codeResult).get("code").asText();
            assertThat(jdbc.queryForObject("SELECT code_hash FROM delivery_otp WHERE delivery_id=?::uuid", String.class, id)).doesNotContain(code);

            // DELIVERED is impossible before verified proof.
            assertThat(status(transition(tenantA, partner, current, "DELIVERED", null))).isEqualTo(409);

            MvcResult proof = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/delivery/deliveries/" + id + "/proof"),
                    partner, Map.of("method", "OTP", "otp", code, "recipientName", "R. Customer"), UUID.randomUUID().toString());
            assertThat(status(proof)).isEqualTo(200);
            assertThat(body(proof).get("verified").asBoolean()).isTrue();

            MvcResult delivered = transition(tenantA, partner, currentDelivery(id, partner), "DELIVERED", null);
            assertThat(status(delivered)).isEqualTo(200);
            assertThat(body(delivered).get("status").asText()).isEqualTo("DELIVERED");

            // History, audit and events all exist for the full path.
            JsonNode finalState = currentDelivery(id, dispatcher(tenantA, 10));
            assertThat(finalState.get("history")).hasSize(8); // CREATED, READY, ASSIGNED, ARRIVED_PICKUP, PICKED_UP, IN_TRANSIT, ARRIVED_DROPOFF, DELIVERED
            assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_audit_event WHERE tenant_id=? AND action='DELIVERY_TRANSITION'", Integer.class, tenantA))
                    .isGreaterThanOrEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM delivery_outbox WHERE tenant_id=? AND aggregate_id=?::uuid", Integer.class, tenantA, id))
                    .isGreaterThanOrEqualTo(8);

            // Order Service is told through the authenticated sync, never through its database.
            long orderId = finalState.get("orderId").asLong();
            assertThat(orderSync.drain()).isEqualTo(3); // PICKED_UP, IN_TRANSIT, DELIVERED
            verify(orderClient).reportDeliveryStatus(eq(orderId), any(), eq(UUID.fromString(id)), eq("PICKED_UP"));
            verify(orderClient).reportDeliveryStatus(eq(orderId), any(), eq(UUID.fromString(id)), eq("IN_TRANSIT"));
            verify(orderClient).reportDeliveryStatus(eq(orderId), any(), eq(UUID.fromString(id)), eq("DELIVERED"));
            assertThat(orderSync.drain()).isZero(); // nothing is delivered twice

            // The one-time code never reaches the logs or the stored audit trail.
            assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage)).noneMatch(message -> message.contains(code));
            assertThat(jdbc.queryForList("SELECT details FROM delivery_audit_event WHERE tenant_id=?", String.class, tenantA))
                    .noneMatch(details -> details != null && details.contains(code));
        } finally {
            root.detachAppender(logs);
        }
    }

    @Test
    void orderSyncRetriesWithBackoffUntilOrderServiceAccepts() throws Exception {
        String partnerId = onboardPartner(tenantA, 502, 10, "Z-" + UUID.randomUUID().toString().substring(0, 8));
        JsonNode ready = readyDelivery(tenantA, 10, 7);
        JsonNode accepted = assignAndAccept(tenantA, 10, 502, ready, partnerId);
        String partner = partner(tenantA, 502);
        MvcResult arrived = transition(tenantA, partner, accepted, "ARRIVED_AT_PICKUP", null);
        assertThat(status(transition(tenantA, partner, body(arrived), "PICKED_UP", null))).isEqualTo(200);

        doThrow(new DeliveryOrderClient.OrderServiceUnavailableException("down", null))
                .when(orderClient).reportDeliveryStatus(anyLong(), any(), any(), any());
        assertThat(orderSync.drain()).isZero();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM delivery_order_sync WHERE tenant_id=?", Integer.class, tenantA)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM delivery_order_sync WHERE tenant_id=?", String.class, tenantA)).isEqualTo("NEW");
        assertThat(orderSync.drain()).isZero(); // backed off: not retried immediately

        doNothing().when(orderClient).reportDeliveryStatus(anyLong(), any(), any(), any());
        jdbc.update("UPDATE delivery_order_sync SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00' WHERE tenant_id=?", tenantA);
        assertThat(orderSync.drain()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM delivery_order_sync WHERE tenant_id=?", String.class, tenantA)).isEqualTo("SYNCED");
    }

    @Test
    void orderEligibilityIsDecidedByOrderServiceAndThePublishedRules() throws Exception {
        String admin = dispatcher(tenantA, 10);

        assertThat(status(postJson("/api/delivery/deliveries/from-order", admin, fromOrderRequest(newOrder(7, "CANCELLED"))))).isEqualTo(409);
        assertThat(status(postJson("/api/delivery/deliveries/from-order", admin, fromOrderRequest(999_999)))).isEqualTo(404);

        doThrow(new DeliveryOrderClient.OrderServiceUnavailableException("down", null)).when(orderClient).findOrder(anyLong());
        assertThat(status(postJson("/api/delivery/deliveries/from-order", admin, fromOrderRequest(newOrder(7, "PLACED"))))).isEqualTo(503);
        reset(orderClient);
        when(orderClient.findOrder(anyLong())).thenAnswer(call -> java.util.Optional.ofNullable(orders.get((Long) call.getArgument(0))));

        long order = newOrder(7, "PLACED");
        assertThat(status(postJson("/api/delivery/deliveries/from-order", admin, fromOrderRequest(order)))).isEqualTo(201);
        // One order backs one delivery platform-wide: another tenant cannot claim it, and it is not reported as existing elsewhere.
        assertThat(status(postJson("/api/delivery/deliveries/from-order", dispatcher(tenantB, 20), fromOrderRequest(order)))).isEqualTo(409);

        // A tenant can tighten eligibility through published rules.
        call(put("/api/delivery/rules/draft"), dispatcher(tenantB, 20), Map.of("rules", Map.of("order.eligible_statuses", List.of("CONFIRMED"))), null);
        assertThat(status(postJson("/api/delivery/rules/draft/publish", dispatcher(tenantB, 20), Map.of()))).isEqualTo(200);
        assertThat(status(postJson("/api/delivery/deliveries/from-order", dispatcher(tenantB, 20), fromOrderRequest(newOrder(7, "PLACED"))))).isEqualTo(409);
        assertThat(status(postJson("/api/delivery/deliveries/from-order", dispatcher(tenantB, 20), fromOrderRequest(newOrder(7, "CONFIRMED"))))).isEqualTo(201);
    }

    @Test
    void callerSuppliedRecipientTenantAndStatusAreIgnored() throws Exception {
        long order = newOrder(7, "PLACED");
        Map<String, Object> request = new java.util.LinkedHashMap<>(fromOrderRequest(order));
        request.put("recipientUserId", 999);
        request.put("tenantId", tenantB.toString());
        request.put("status", "DELIVERED");
        request.put("orderTotal", "0.01");
        MvcResult created = postJson("/api/delivery/deliveries/from-order", dispatcher(tenantA, 10), request);
        assertThat(status(created)).isEqualTo(201);
        JsonNode delivery = body(created);
        assertThat(delivery.get("recipientUserId").asLong()).isEqualTo(7);
        assertThat(delivery.get("tenantId").asText()).isEqualTo(tenantA.toString());
        assertThat(delivery.get("status").asText()).isEqualTo("CREATED");
        assertThat(new BigDecimal(delivery.get("orderTotal").asText())).isEqualByComparingTo("49.90");
    }
}

package com.ecommerce.order;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderReturnRepository;
import com.ecommerce.order.repository.OrderTrackingEventRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.ecommerce.order.service.ReturnStockClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Order Service stays authoritative: Delivery Service can only report progress, and Order Service applies its own rules. */
@SpringBootTest(properties = "SERVICE_TO_SERVICE_TOKEN=test-service-token")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryStatusSyncTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private OrderRepository orders;
    @Autowired private OrderReturnRepository returns;
    @Autowired private OrderTrackingEventRepository tracking;
    @MockBean private OrderProducerService producer;
    @MockBean private ReturnStockClient returnStockClient;

    @BeforeEach
    void clean() {
        returns.deleteAll();
        tracking.deleteAll();
        orders.deleteAll();
    }

    private Order order(String status) {
        Order order = new Order();
        order.setUserId(7L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("49.90"));
        return orders.save(order);
    }

    private org.springframework.test.web.servlet.ResultActions report(long orderId, String deliveryStatus, String token) throws Exception {
        var request = post("/internal/order-data/" + orderId + "/delivery-status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"e1\",\"deliveryId\":\"d1\",\"deliveryStatus\":\"" + deliveryStatus + "\"}");
        if (token != null) request.header("X-Service-Token", token);
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("Delivery progress moves the order forward, once, and completes it on delivery")
    void appliesProgressIdempotentlyAndCompletesTheOrder() throws Exception {
        Order order = order("PLACED");

        report(order.getId(), "PICKED_UP", "test-service-token").andExpect(status().isOk()).andExpect(jsonPath("$.applied", is(true)));
        report(order.getId(), "PICKED_UP", "test-service-token").andExpect(jsonPath("$.applied", is(false)));       // duplicate
        report(order.getId(), "IN_TRANSIT", "test-service-token").andExpect(jsonPath("$.applied", is(true)));
        report(order.getId(), "PICKED_UP", "test-service-token").andExpect(jsonPath("$.applied", is(false)));       // late, out of order
        assertThat(orders.findById(order.getId()).orElseThrow().getShippingStatus()).isEqualTo("OUT_FOR_DELIVERY");

        report(order.getId(), "DELIVERED", "test-service-token").andExpect(jsonPath("$.applied", is(true)));
        Order done = orders.findById(order.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo("DELIVERED");
        assertThat(done.getShippingStatus()).isEqualTo("DELIVERED");
        assertThat(done.getTotalAmount()).isEqualByComparingTo("49.90");                                            // totals untouched
        report(order.getId(), "IN_TRANSIT", "test-service-token").andExpect(jsonPath("$.applied", is(false)));      // finished orders are final
        assertThat(tracking.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("Cancelled orders are never revived by a delivery report")
    void cancelledOrdersAreUntouched() throws Exception {
        Order order = order("CANCELLED");
        report(order.getId(), "DELIVERED", "test-service-token").andExpect(jsonPath("$.applied", is(false)));
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Reports without the service credential, with a wrong one, for unknown orders or with unknown statuses are refused")
    void refusesUnauthenticatedUnknownAndInvalidReports() throws Exception {
        Order order = order("PLACED");
        report(order.getId(), "DELIVERED", null).andExpect(status().isUnauthorized());
        report(order.getId(), "DELIVERED", "wrong-token").andExpect(status().isUnauthorized());
        report(order.getId() + 1000, "DELIVERED", "test-service-token").andExpect(status().isNotFound());
        report(order.getId(), "TELEPORTED", "test-service-token").andExpect(status().isBadRequest());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo("PLACED");
    }
}

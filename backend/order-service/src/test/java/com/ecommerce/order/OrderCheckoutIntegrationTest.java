package com.ecommerce.order;

import com.ecommerce.order.dto.CartItemDto;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.dto.OrderStatusUpdateRequest;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OrderCheckoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private com.ecommerce.order.repository.OrderTrackingEventRepository trackingRepository;

    @Autowired(required = false)
    private com.ecommerce.order.repository.OrderReturnRepository returnRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderProducerService orderProducerService;

    @BeforeEach
    void setUp() {
        if (returnRepository != null) returnRepository.deleteAll();
        if (trackingRepository != null) trackingRepository.deleteAll();
        orderRepository.deleteAll();
        when(orderProducerService.publishOrderEvent(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("POST /api/orders/checkout should place order and return total")
    void testCheckout() throws Exception {
        CheckoutRequest request = new CheckoutRequest();
        request.setUserId(10L);

        CartItemDto item = new CartItemDto();
        item.setProductId(1L);
        item.setName("Wireless Mouse");
        item.setPrice(new BigDecimal("25.00"));
        item.setQuantity(2);
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.totalAmount").value(50.00))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].productName").value("Wireless Mouse"));

        List<Order> orders = orderRepository.findByUserId(10L);
        assertThat(orders).hasSize(1);
    }

    @Test
    @DisplayName("GET /api/orders/user/{userId} should retrieve orders for customer")
    void testGetOrdersByUser() throws Exception {
        Order order = new Order();
        order.setUserId(5L);
        order.setStatus("PLACED");
        order.setTotalAmount(new BigDecimal("120.00"));

        OrderItem lineItem = new OrderItem();
        lineItem.setProductId(2L);
        lineItem.setProductName("Headphones");
        lineItem.setPrice(new BigDecimal("120.00"));
        lineItem.setQuantity(1);
        order.addItem(lineItem);

        orderRepository.save(order);

        mockMvc.perform(get("/api/orders/user/5").header("X-User-Id", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(5))
                .andExpect(jsonPath("$[0].totalAmount").value(120.00));
    }

    @Test
    @DisplayName("GET /api/orders/user/{userId} denies other customers and look-alike admin roles")
    void testGetOrdersByUserRequiresOwnerOrExactAdminRole() throws Exception {
        mockMvc.perform(get("/api/orders/user/5"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/user/5").header("X-User-Id", 6L))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/user/5").header("X-User-Id", 6L).header("X-User-Role", "DELIVERY_ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/user/5").header("X-User-Id", 6L).header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/orders/{id}/status should transition order state")
    void testUpdateOrderStatus() throws Exception {
        Order order = new Order();
        order.setUserId(5L);
        order.setStatus("PLACED");
        order.setTotalAmount(new BigDecimal("75.00"));
        Order saved = orderRepository.save(order);

        OrderStatusUpdateRequest update = new OrderStatusUpdateRequest();
        // The state machine now guards this endpoint: PLACED cannot jump straight to SHIPPED...
        update.setStatus("SHIPPED");
        mockMvc.perform(put("/api/orders/" + saved.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isConflict());
        assertThat(orderRepository.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo("PLACED");

        // ...but a valid step is accepted.
        update.setStatus("CONFIRMED");
        mockMvc.perform(put("/api/orders/" + saved.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("CONFIRMED");
    }
}

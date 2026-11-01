package com.ecommerce.order;

import com.ecommerce.order.dto.CartItemDto;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.PricingClient;
import com.ecommerce.order.service.StorefrontClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checkout through a named storefront: tenant, store and type come from the storefront, prices from that tenant's pricing. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorefrontCheckoutTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID LEGACY = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private OrderProducerService producer;
    @MockBean private PricingClient pricing;
    @MockBean private StorefrontClient storefronts;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        when(producer.publishOrderEvent(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pricing.enabled()).thenReturn(false); // the deployment-wide (legacy) pricing flag is off
        when(storefronts.resolve("acme")).thenReturn(new StorefrontClient.Storefront(TENANT, "main", "RETAIL", "acme"));
        when(storefronts.resolve("bistro")).thenReturn(new StorefrontClient.Storefront(TENANT, "kitchen", "RESTAURANT", "bistro"));
        when(storefronts.resolve("nope")).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown storefront."));
    }

    private CheckoutRequest request(String storefront, String clientPrice) {
        CartItemDto item = new CartItemDto();
        item.setProductId(1L); item.setName("Mouse"); item.setPrice(new BigDecimal(clientPrice)); item.setQuantity(2);
        CheckoutRequest r = new CheckoutRequest();
        r.setUserId(5L); r.setItems(List.of(item)); r.setStorefront(storefront);
        r.setStoreId("someone-elses-store"); r.setOrderType("RETAIL");
        return r;
    }

    @Test
    @DisplayName("the order belongs to the storefront's tenant and store and is priced in that tenant even with the legacy pricing flag off")
    void orderTakesStorefrontTenant() {
        when(pricing.redeem(eq(TENANT), anyString(), any(), any(), any())).thenReturn(Map.of("lines", List.of(Map.of("unitPrice", "40.00")), "discount", "0.00"));
        Order order = orderService.placeOrder(request("acme", "0.01"));
        Order saved = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getStoreId()).isEqualTo("main");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("80.00");
        verify(pricing).redeem(eq(TENANT), startsWith("chk-"), eq(5L), any(), any());
        verify(pricing, never()).redeem(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("a restaurant storefront makes a restaurant order whatever orderType the client sent")
    void restaurantStorefront() {
        when(pricing.redeem(eq(TENANT), anyString(), any(), any(), any())).thenReturn(Map.of("lines", List.of(Map.of("unitPrice", "12.00")), "discount", "0.00"));
        Order order = orderService.placeOrder(request("bistro", "12.00"));
        assertThat(order.getOrderType()).isEqualTo("RESTAURANT");
        // RECEIVED is the restaurant start state; with no stock-service in the test the inventory hold parks it as PENDING_INVENTORY.
        assertThat(order.getStatus()).isIn("RECEIVED", "PENDING_INVENTORY").isNotEqualTo("PLACED");
        assertThat(order.getStoreId()).isEqualTo("kitchen");
    }

    @Test
    @DisplayName("a product the storefront's tenant does not sell is refused and no order is created")
    void foreignProductRefused() {
        when(pricing.redeem(eq(TENANT), anyString(), any(), any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Product 1", null, null, null));
        assertThatThrownBy(() -> orderService.placeOrder(request("acme", "40.00")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("an unknown storefront is refused before anything is priced")
    void unknownStorefront() {
        assertThatThrownBy(() -> orderService.placeOrder(request("nope", "40.00"))).isInstanceOf(ResponseStatusException.class);
        verify(pricing, never()).redeem(any(UUID.class), anyString(), any(), any(), any());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("without a storefront the legacy single-tenant checkout is unchanged")
    void legacyCheckoutUnchanged() {
        Order order = orderService.placeOrder(request(null, "25.00"));
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getTenantId()).isEqualTo(LEGACY);
        assertThat(order.getStoreId()).isEqualTo("someone-elses-store");
        verifyNoInteractions(storefronts);
    }

    @Test
    @DisplayName("checkout uses the authenticated user, not a userId in the request body")
    void bodyUserIdCannotImpersonate() throws Exception {
        CheckoutRequest body = request(null, "25.00");
        body.setUserId(99L);
        mvc.perform(post("/api/orders/checkout").header("X-User-Id", 7L)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
        assertThat(orderRepository.findAll()).singleElement().satisfies(o -> assertThat(o.getUserId()).isEqualTo(7L));
    }
}

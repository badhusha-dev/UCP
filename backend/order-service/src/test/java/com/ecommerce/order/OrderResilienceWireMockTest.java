package com.ecommerce.order;

import com.ecommerce.order.dto.CartItemDto;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.service.OrderProducerService;
import com.ecommerce.order.service.OrderService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class OrderResilienceWireMockTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private OrderService orderService;

    @MockBean
    private OrderProducerService orderProducerService;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(9998));
        wireMockServer.start();
        WireMock.configureFor("localhost", 9998);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should create PLACED order when Product Service inventory deduction succeeds")
    void testOrderPlacementHealthyProductService() {
        when(orderProducerService.publishOrderEvent(any())).thenReturn(CompletableFuture.completedFuture(null));

        wireMockServer.stubFor(post(urlEqualTo("/api/stock/adjustments"))
                .withRequestBody(matchingJsonPath("$.productId", equalTo("10")))
                .withRequestBody(matchingJsonPath("$.delta", equalTo("-1")))
                .willReturn(aResponse()
                        .withStatus(200)));

        CheckoutRequest request = new CheckoutRequest();
        request.setUserId(1L);
        CartItemDto item = new CartItemDto();
        item.setProductId(10L);
        item.setName("Smart Watch");
        item.setPrice(new BigDecimal("199.99"));
        item.setQuantity(1);
        request.setItems(List.of(item));

        Order order = orderService.placeOrder(request);

        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo("PLACED");
        assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("199.99"));
    }

    @Test
    @DisplayName("Should create PENDING_INVENTORY order when Product Service fails (Circuit Breaker Fallback)")
    void testOrderPlacementDegradedProductService() {
        when(orderProducerService.publishOrderEvent(any())).thenReturn(CompletableFuture.completedFuture(null));

        wireMockServer.stubFor(post(urlEqualTo("/api/stock/adjustments"))
                .withRequestBody(matchingJsonPath("$.productId", equalTo("20")))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        CheckoutRequest request = new CheckoutRequest();
        request.setUserId(2L);
        CartItemDto item = new CartItemDto();
        item.setProductId(20L);
        item.setName("Laptop Sleeve");
        item.setPrice(new BigDecimal("29.99"));
        item.setQuantity(1);
        request.setItems(List.of(item));

        Order order = orderService.placeOrder(request);

        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo("PENDING_INVENTORY");
    }
}

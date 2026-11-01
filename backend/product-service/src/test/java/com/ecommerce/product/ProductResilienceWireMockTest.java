package com.ecommerce.product;

import com.ecommerce.product.service.ProductService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ProductResilienceWireMockTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private ProductService productService;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(9999));
        wireMockServer.start();
        WireMock.configureFor("localhost", 9999);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should fetch system orders when downstream Order Service is healthy")
    void testGetSystemOrdersSuccess() {
        wireMockServer.stubFor(get(urlEqualTo("/api/orders"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\": 101, \"status\": \"CONFIRMED\"}]")
                        .withStatus(200)));

        List<Object> orders = productService.getSystemOrders();
        assertThat(orders).isNotEmpty();
        assertThat(orders.toString()).contains("101");
    }

    @Test
    @DisplayName("Should invoke Resilience4j Circuit Breaker fallback when Order Service fails")
    void testGetSystemOrdersCircuitBreakerFallback() {
        wireMockServer.stubFor(get(urlEqualTo("/api/orders"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        List<Object> fallbackResponse = productService.getSystemOrders();
        assertThat(fallbackResponse).isNotEmpty();
        assertThat(fallbackResponse.get(0).toString()).contains("Fallback: Order Service is currently unavailable");
    }
}

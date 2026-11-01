package com.ecommerce.order;

import com.ecommerce.order.dto.CartItemDto;
import com.ecommerce.order.dto.CheckoutRequest;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.PricingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** With the pricing integration on, the payable amount comes from the pricing module and client prices are ignored. */
@SpringBootTest
@ActiveProfiles("test")
class OrderPricingCheckoutTest {
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @MockBean private OrderProducerService producer;
    @MockBean private PricingClient pricing;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        when(pricing.enabled()).thenReturn(true);
    }

    private CheckoutRequest request(String clientPrice) {
        CartItemDto item = new CartItemDto();
        item.setProductId(1L); item.setName("Mouse"); item.setPrice(new BigDecimal(clientPrice)); item.setQuantity(2);
        CheckoutRequest r = new CheckoutRequest();
        r.setUserId(5L); r.setItems(List.of(item)); r.setCouponCode("SPRING10");
        return r;
    }

    @Test
    @DisplayName("server-computed unit price, discount and total replace anything the client sent")
    void serverPriceWins() {
        when(pricing.redeem(anyString(), any(), any(), any())).thenReturn(Map.of(
                "lines", List.of(Map.of("unitPrice", "100.00")), "discount", "20.00", "couponValid", true, "couponCode", "SPRING10"));
        Order order = orderService.placeOrder(request("0.01")); // tampered client price
        assertThat(order.getTotalAmount()).isEqualByComparingTo("180.00");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getItems().get(0).getPrice()).isEqualByComparingTo("100.00");
        assertThat(order.getCouponCode()).isEqualTo("SPRING10");
        assertThat(order.getPromotionRef()).startsWith("chk-");
    }

    @Test
    @DisplayName("cancelling an order releases its promotions; a reversal failure does not block the cancel")
    void cancelReleasesPromotions() {
        when(pricing.redeem(anyString(), any(), any(), any())).thenReturn(Map.of("lines", List.of(Map.of("unitPrice", "100.00")), "discount", "0.00"));
        Order order = orderService.placeOrder(request("100.00"));
        doThrow(new IllegalStateException("pricing down")).when(pricing).reverse(order.getPromotionRef());
        assertThat(orderService.cancelOrder(order.getId()).getStatus()).isEqualTo("CANCELLED");
        verify(pricing).reverse(order.getTenantId(), order.getPromotionRef());
    }

    @Test
    @DisplayName("a refused redemption blocks the order; a later failure releases the redemption")
    void redemptionFailureAndRelease() {
        when(pricing.redeem(anyString(), any(), any(), any())).thenThrow(new IllegalStateException("coupon exhausted"));
        try { orderService.placeOrder(request("100.00")); } catch (IllegalStateException expected) { /* order not created */ }
        assertThat(orderRepository.count()).isZero();
        verify(pricing, never()).reverse(anyString());

        reset(pricing);
        when(pricing.enabled()).thenReturn(true);
        when(pricing.redeem(anyString(), any(), any(), any())).thenReturn(Map.of("lines", List.of(), "discount", "0.00")); // lines mismatch -> failure after redeem
        try { orderService.placeOrder(request("100.00")); } catch (RuntimeException expected) { /* failed after redeem */ }
        verify(pricing, times(1)).reverse(anyString());
        assertThat(orderRepository.count()).isZero();
    }
}

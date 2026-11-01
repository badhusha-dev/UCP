package com.ecommerce.order;

import com.ecommerce.order.dto.*;
import com.ecommerce.order.model.Coupon;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.CouponRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderReturnRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.ecommerce.order.service.ReturnStockClient;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdvancedSalesFulfillmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OrderReturnRepository returnRepository;

    @Autowired
    private com.ecommerce.order.repository.OrderTrackingEventRepository trackingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderProducerService orderProducerService;

    @MockBean
    private ReturnStockClient returnStockClient;

    @BeforeEach
    void setUp() {
        returnRepository.deleteAll();
        trackingRepository.deleteAll();
        orderRepository.deleteAll();
        couponRepository.deleteAll();

        // Seed test coupon
        Coupon coupon = new Coupon();
        coupon.setCode("PROMO10");
        coupon.setDiscountType("PERCENTAGE");
        coupon.setDiscountValue(new BigDecimal("10.00"));
        coupon.setMinOrderAmount(new BigDecimal("20.00"));
        coupon.setMaxDiscount(new BigDecimal("50.00"));
        coupon.setUsageLimit(100);
        coupon.setTimesUsed(0);
        coupon.setStartDate(OffsetDateTime.now().minusDays(1));
        coupon.setExpiryDate(OffsetDateTime.now().plusDays(30));
        coupon.setActive(true);
        couponRepository.save(coupon);
    }

    @Test
    @DisplayName("POST /api/orders/coupons/validate should validate discount correctly")
    void testValidateCoupon() throws Exception {
        ValidateCouponRequest request = new ValidateCouponRequest();
        request.setCouponCode("PROMO10");
        request.setOrderAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/orders/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.couponCode").value("PROMO10"))
                .andExpect(jsonPath("$.discountAmount").value(10.00))
                .andExpect(jsonPath("$.finalAmount").value(90.00));
    }

    @Test
    @DisplayName("POST /api/orders/coupons/validate should reject order below minimum spend")
    void testValidateCouponBelowMinSpend() throws Exception {
        ValidateCouponRequest request = new ValidateCouponRequest();
        request.setCouponCode("PROMO10");
        request.setOrderAmount(new BigDecimal("15.00"));

        mockMvc.perform(post("/api/orders/coupons/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value(containsString("requires a minimum purchase")));
    }

    @Test
    @DisplayName("POST /api/orders/checkout should apply coupon discount and deduct from total")
    void testCheckoutWithCoupon() throws Exception {
        CheckoutRequest request = new CheckoutRequest();
        request.setUserId(42L);
        request.setCouponCode("PROMO10");

        CartItemDto item = new CartItemDto();
        item.setProductId(1L);
        item.setName("Ergonomic Keyboard");
        item.setPrice(new BigDecimal("100.00"));
        item.setQuantity(1);
        request.setItems(List.of(item));

        mockMvc.perform(post("/api/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponCode").value("PROMO10"))
                .andExpect(jsonPath("$.discountAmount").value(10.00))
                .andExpect(jsonPath("$.totalAmount").value(90.00))
                .andExpect(jsonPath("$.carrier").value("FedEx Express"))
                .andExpect(jsonPath("$.trackingNumber").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/orders/cart/sync should persist and retrieve customer cart")
    void testPersistentCartSync() throws Exception {
        CartSyncRequest syncRequest = new CartSyncRequest();
        syncRequest.setItemsJson("[{\"productId\":1,\"name\":\"Headphones\",\"quantity\":1}]");

        mockMvc.perform(post("/api/orders/cart/sync")
                        .header("X-User-Id", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(99))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.itemsJson").value(containsString("Headphones")));

        mockMvc.perform(get("/api/orders/cart")
                        .header("X-User-Id", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(99))
                .andExpect(jsonPath("$.itemsJson").value(containsString("Headphones")));
    }

    @Test
    @DisplayName("GET /api/orders/{id}/tracking should return order tracking timeline")
    void testOrderTracking() throws Exception {
        Order order = new Order();
        order.setUserId(5L);
        order.setStatus("PLACED");
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setCarrier("DHL Express");
        order.setTrackingNumber("TRK-12345");
        order.setShippingStatus("PROCESSING");
        Order saved = orderRepository.save(order);

        mockMvc.perform(get("/api/orders/" + saved.getId() + "/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(saved.getId()))
                .andExpect(jsonPath("$.carrier").value("DHL Express"))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-12345"))
                .andExpect(jsonPath("$.events", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Return lifecycle: Request -> Approve -> Receive & restock -> Refund")
    void testReturnLifecycle() throws Exception {
        Order order = new Order();
        order.setUserId(20L);
        order.setStatus("DELIVERED");
        order.setTotalAmount(new BigDecimal("75.00"));
        OrderItem line = new OrderItem();
        line.setProductId(77L);
        line.setProductName("Desk Lamp");
        line.setPrice(new BigDecimal("75.00"));
        line.setQuantity(1);
        order.addItem(line);
        Order savedOrder = orderRepository.save(order);
        Long orderItemId = savedOrder.getItems().get(0).getId();

        // 1. Customer requests a return for the purchased line item
        ReturnLineRequest returnLine = new ReturnLineRequest();
        returnLine.setOrderItemId(orderItemId);
        returnLine.setQuantity(1);
        CreateReturnRequest returnReq = new CreateReturnRequest();
        returnReq.setReason("Item defective upon unboxing");
        returnReq.setItems(List.of(returnLine));

        String returnResponse = mockMvc.perform(post("/api/orders/" + savedOrder.getId() + "/returns")
                        .header("X-User-Id", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.refundAmount").value(75.00))
                .andReturn().getResponse().getContentAsString();

        Long returnId = objectMapper.readTree(returnResponse).get("id").asLong();

        // 2. The old header-trusting staff endpoint is retired; the lifecycle lives in the tenant-scoped RMA API (see ReturnsApiIntegrationTest)
        processReturn(returnId, "REFUND", "MANAGER").andExpect(status().isGone());
        processReturn(returnId, "APPROVE", "ADMIN").andExpect(status().isGone());
        mockMvc.perform(get("/api/orders/returns").header("X-User-Role", "ADMIN")).andExpect(status().isGone());
        org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject("SELECT rma_number FROM order_return WHERE id=?", String.class, returnId).startsWith("RMA-"));
    }

    @Test
    @DisplayName("Returns cannot be requested before delivery")
    void testReturnRequiresDeliveredOrder() throws Exception {
        Order order = new Order();
        order.setUserId(21L);
        order.setStatus("CONFIRMED");
        order.setTotalAmount(new BigDecimal("40.00"));
        OrderItem line = new OrderItem();
        line.setProductId(78L);
        line.setProductName("Mug");
        line.setPrice(new BigDecimal("40.00"));
        line.setQuantity(1);
        order.addItem(line);
        Order savedOrder = orderRepository.save(order);

        ReturnLineRequest returnLine = new ReturnLineRequest();
        returnLine.setOrderItemId(savedOrder.getItems().get(0).getId());
        returnLine.setQuantity(1);
        CreateReturnRequest returnReq = new CreateReturnRequest();
        returnReq.setReason("Changed my mind");
        returnReq.setItems(List.of(returnLine));

        mockMvc.perform(post("/api/orders/" + savedOrder.getId() + "/returns")
                        .header("X-User-Id", 21L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isUnprocessableEntity());
    }

    private org.springframework.test.web.servlet.ResultActions processReturn(Long returnId, String action, String role) throws Exception {
        ProcessReturnRequest processReq = new ProcessReturnRequest();
        processReq.setAction(action);
        processReq.setResolutionNotes("Returned item received in warehouse and inspected.");
        var request = post("/api/orders/returns/" + returnId + "/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(processReq));
        if (role != null) request = request.header("X-User-Role", role);
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("GET /api/orders/analytics/executive should return platform commerce metrics")
    void testExecutiveAnalytics() throws Exception {
        Order order1 = new Order();
        order1.setUserId(1L);
        order1.setStatus("CONFIRMED");
        order1.setTotalAmount(new BigDecimal("200.00"));
        order1.setDiscountAmount(new BigDecimal("20.00"));
        orderRepository.save(order1);

        mockMvc.perform(get("/api/orders/analytics/executive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossMerchandiseValue").value(220.00))
                .andExpect(jsonPath("$.totalDiscounts").value(20.00))
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.platformDisclaimer").isNotEmpty());
    }
}

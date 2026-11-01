package com.ecommerce.order;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderReturn;
import com.ecommerce.order.repository.OrderItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.repository.OrderReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @BeforeEach
    void setUp() {
        orderReturnRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();

        // Seed 3 orders with different statuses and items
        // Order 1: Completed, $250.00, user 101
        Order o1 = new Order();
        o1.setUserId(101L);
        o1.setStatus("DELIVERED");
        o1.setTotalAmount(new BigDecimal("250.00"));
        o1.setDiscountAmount(new BigDecimal("25.00"));
        o1.setCouponCode("SAVE25");
        o1.setCreatedAt(LocalDateTime.now().minusDays(3));

        OrderItem item1 = new OrderItem();
        item1.setProductId(1L);
        item1.setProductName("Wireless Headphones");
        item1.setPrice(new BigDecimal("125.00"));
        item1.setQuantity(2);
        o1.addItem(item1);
        orderRepository.save(o1);

        // Order 2: Placed, $150.00, user 102
        Order o2 = new Order();
        o2.setUserId(102L);
        o2.setStatus("PLACED");
        o2.setTotalAmount(new BigDecimal("150.00"));
        o2.setDiscountAmount(BigDecimal.ZERO);
        o2.setCreatedAt(LocalDateTime.now().minusDays(1));

        OrderItem item2 = new OrderItem();
        item2.setProductId(2L);
        item2.setProductName("Mechanical Keyboard");
        item2.setPrice(new BigDecimal("150.00"));
        item2.setQuantity(1);
        o2.addItem(item2);
        orderRepository.save(o2);

        // Order 3: Cancelled, $80.00, user 103 (must be excluded from GMV/Net Sales)
        Order o3 = new Order();
        o3.setUserId(103L);
        o3.setStatus("CANCELLED");
        o3.setTotalAmount(new BigDecimal("80.00"));
        o3.setDiscountAmount(BigDecimal.ZERO);
        o3.setCreatedAt(LocalDateTime.now().minusHours(5));
        orderRepository.save(o3);

        // Refund on order 1: $50.00
        OrderReturn ret = new OrderReturn();
        ret.setOrder(o1);
        ret.setUserId(101L);
        ret.setReason("Minor cosmetic scratch");
        ret.setStatus("REFUNDED");
        ret.setRefundAmount(new BigDecimal("50.00"));
        ret.setCreatedAt(OffsetDateTime.now().minusDays(2));
        orderReturnRepository.save(ret);
    }

    @Test
    @DisplayName("RBAC: Rejects requests without Admin/Manager role with 403 Forbidden")
    void testSecurityRoleEnforcement() throws Exception {
        mockMvc.perform(get("/api/orders/admin/analytics/overview")
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/admin/analytics/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Overview: Correctly aggregates GMV, Net Sales, AOV, cancellations, and refunds")
    void testOverviewAnalyticsAggregation() throws Exception {
        // Expected:
        // GMV = (250 + 25) + (150 + 0) = 425.00 (Cancelled order excluded)
        // Net Sales = (250 + 150) - 50 = 350.00
        // Orders Placed = 3
        // Delivered Orders = 1
        // Cancelled Orders = 1, Cancellation Rate = (1/3)*100 = 33.3%
        // AOV = Net Sales / valid orders = 350.00 / 2 = 175.00
        // Refunds = 50.00

        mockMvc.perform(get("/api/orders/admin/analytics/overview")
                        .header("X-User-Role", "ADMIN")
                        .param("timeRange", "LAST_7_DAYS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossMerchandiseValue.current", is(425.00)))
                .andExpect(jsonPath("$.netSales.current", is(350.00)))
                .andExpect(jsonPath("$.ordersPlaced.current", is(3)))
                .andExpect(jsonPath("$.deliveredOrders.current", is(1)))
                .andExpect(jsonPath("$.cancelledOrders.current", is(1)))
                .andExpect(jsonPath("$.cancellationRate.current", is(33.3)))
                .andExpect(jsonPath("$.averageOrderValue.current", is(175.00)))
                .andExpect(jsonPath("$.refundAmount.current", is(50.00)))
                .andExpect(jsonPath("$.unitsSold.current", is(3)))
                .andExpect(jsonPath("$.timezone", is("UTC")));
    }

    @Test
    @DisplayName("Sales Series: Returns chronological points and status breakdown")
    void testSalesAnalyticsSeries() throws Exception {
        mockMvc.perform(get("/api/orders/admin/analytics/sales")
                        .header("X-User-Role", "MANAGER")
                        .param("timeRange", "LAST_7_DAYS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailySeries", hasSize(greaterThanOrEqualTo(7))))
                .andExpect(jsonPath("$.statusBreakdown.DELIVERED", is(1)))
                .andExpect(jsonPath("$.statusBreakdown.PLACED", is(1)))
                .andExpect(jsonPath("$.statusBreakdown.CANCELLED", is(1)));
    }

    @Test
    @DisplayName("Products: Returns top products by revenue and category performance")
    void testProductAnalytics() throws Exception {
        mockMvc.perform(get("/api/orders/admin/analytics/products")
                        .header("X-User-Role", "ADMIN")
                        .param("timeRange", "LAST_7_DAYS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topProductsByRevenue", hasSize(2)))
                .andExpect(jsonPath("$.topProductsByRevenue[0].productName", is("Wireless Headphones")))
                .andExpect(jsonPath("$.categoryPerformance", is(not(empty()))));
    }

    @Test
    @DisplayName("Customers: Returns new vs returning counts and top spenders")
    void testCustomerAnalytics() throws Exception {
        mockMvc.perform(get("/api/orders/admin/analytics/customers")
                        .header("X-User-Role", "ADMIN")
                        .param("timeRange", "LAST_7_DAYS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUniqueCustomers", is(3)))
                .andExpect(jsonPath("$.topCustomers", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("Orders Drill-down: Paginates and filters orders by status")
    void testOrdersFilteredPagination() throws Exception {
        mockMvc.perform(get("/api/orders/admin/analytics/orders")
                        .header("X-User-Role", "ADMIN")
                        .param("status", "DELIVERED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status", is("DELIVERED")))
                .andExpect(jsonPath("$.content[0].totalAmount", is(250.00)));
    }

    @Test
    @DisplayName("CSV Export: Protects against formula injection in exported files")
    void testCsvExportWithFormulaInjectionDefense() throws Exception {
        // Seed order with potential formula injection title/code
        Order injectOrder = new Order();
        injectOrder.setUserId(999L);
        injectOrder.setStatus("PROCESSING");
        injectOrder.setTotalAmount(new BigDecimal("99.00"));
        injectOrder.setCouponCode("=cmd|'/C calc'!A0");
        orderRepository.save(injectOrder);

        mockMvc.perform(get("/api/orders/admin/analytics/reports/export")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("ucp_orders_report_")))
                .andExpect(content().string(containsString("Order ID,Created At (UTC)")))
                // Ensure formula injection prefix `'` is inserted
                .andExpect(content().string(containsString("'=cmd|'/C calc'!A0")));
    }

    @Test
    @DisplayName("Alerts: Detects inactive carts and operational anomalies")
    void testAlertsEndpoint() throws Exception {
        mockMvc.perform(get("/api/orders/admin/analytics/alerts")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highCancellationRateAlert", is(notNullValue())))
                .andExpect(jsonPath("$.delayedFulfillmentCount", is(notNullValue())));
    }
}

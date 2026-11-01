package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminAuthorizationClient;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Order Management Control Tower over HTTP: real migration SQL on H2, real JWT verification, real state machine. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"SERVICE_TO_SERVICE_TOKEN=test-service-token", "orders.exceptions.scan-initial-delay-ms=3600000"})
class OrderAdminApiIntegrationTest {
    private static final UUID A = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");
    private static final List<String> ALL = List.of("order:view", "order:view-all-stores", "order:customer:view", "order:confirm", "order:process", "order:hold", "order:release",
            "order:cancel", "order:export", "order:bulk", "order:fulfillment:view", "order:exception:manage", "order:note:create", "order:audit:view", "order:report:view", "order:settings");
    private static final String API = "/api/orders/admin";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private OrderRepository orders;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ExceptionService exceptionService;
    @MockBean private AdminAuthorizationClient grants;
    @MockBean private OrderProducerService producer;
    @Value("${jwt.secret}") private String secret;

    @BeforeEach
    void reset() {
        wipe();
        doNothing().when(grants).requireCurrentGrant(anyString());
    }

    @AfterEach
    void cleanUp() { wipe(); }

    private void wipe() {
        for (String t : List.of("order_processed_events", "order_bulk_job_items", "order_bulk_jobs", "order_saved_views", "order_exception_events", "order_exceptions", "order_fulfillment_items",
                "order_fulfillments", "order_notes", "order_admin_actions", "order_status_history", "order_settings")) jdbc.update("DELETE FROM " + t);
        jdbc.update("DELETE FROM order_return_items");
        jdbc.update("DELETE FROM order_return");
        jdbc.update("DELETE FROM order_tracking_event");
        orders.deleteAll();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private String token(UUID tenant, long user, List<String> perms, List<String> stores) {
        var b = Jwts.builder().setSubject("u" + user + "@" + tenant).setId(UUID.randomUUID().toString()).setIssuer("universal-commerce").setAudience("commerce-api")
                .setExpiration(new Date(System.currentTimeMillis() + 600_000)).claim("id", user).claim("tenant_id", tenant.toString()).claim("tenant_role", "ORDER_MANAGER")
                .claim("application_code", "ECOMMERCE_ADMIN").claim("permissions", perms);
        if (stores != null) b.claim("store_ids", stores);
        return b.signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
    }
    private String admin(UUID t) { return token(t, 1, ALL, null); }
    private String without(UUID t, String... removed) { List<String> p = new ArrayList<>(ALL); p.removeAll(List.of(removed)); return token(t, 7, p, null); }

    private JsonNode call(MockHttpServletRequestBuilder b, String token, Object body, int status, String key) throws Exception {
        if (token != null) b = b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (key != null) b = b.header("Idempotency-Key", key);
        if (body != null) b = b.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        MvcResult r = mvc.perform(b).andExpect(status().is(status)).andReturn();
        String c = r.getResponse().getContentAsString();
        return c.startsWith("{") || c.startsWith("[") ? json.readTree(c) : null;
    }
    private JsonNode call(MockHttpServletRequestBuilder b, String token, Object body, int status) throws Exception { return call(b, token, body, status, null); }

    private int seq = 0;
    private String key() { return "key-" + System.nanoTime() + "-" + (seq++); }

    private Order order(UUID tenant, String store, String type, String status, String total, String customer) {
        Order o = new Order();
        o.setTenantId(tenant); o.setStoreId(store); o.setOrderType(type); o.setStatus(status); o.setUserId(42L);
        o.setTotalAmount(new BigDecimal(total)); o.setCustomerName(customer); o.setCustomerEmail(customer == null ? null : customer.toLowerCase().replace(' ', '.') + "@example.com");
        o.setCustomerPhone("+1 555 0100"); o.setCreatedAt(LocalDateTime.now());
        OrderItem i = new OrderItem(); i.setProductId(1L); i.setProductName("Widget"); i.setPrice(new BigDecimal("10.00")); i.setQuantity(5);
        o.addItem(i);
        return orders.save(o);
    }
    private Order retail(UUID t, String status) { return order(t, "main", "RETAIL", status, "50.00", "Alice Smith"); }

    private JsonNode transition(UUID tenant, long id, String to, String reason, int status) throws Exception {
        return call(post(API + "/orders/" + id + "/transition"), admin(tenant), Map.of("to", to, "reason", reason == null ? "" : reason), status, key());
    }
    private void walk(UUID tenant, long id, String... steps) throws Exception { for (String s : steps) transition(tenant, id, s, null, 200); }
    private String statusOf(long id) { return orders.findById(id).orElseThrow().getStatus(); }

    // ---- authentication, permissions, tenant isolation --------------------------------------------------------------

    @Test @DisplayName("requests without a valid admin token are rejected; permission-less callers get 403")
    void authentication() throws Exception {
        mvc.perform(get(API + "/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/orders").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
        String wrongApp = Jwts.builder().setSubject("x").setId("1").setIssuer("universal-commerce").setAudience("commerce-api").setExpiration(new Date(System.currentTimeMillis() + 60000))
                .claim("id", 1).claim("tenant_id", A.toString()).claim("tenant_role", "X").claim("application_code", "INVENTORY").claim("permissions", ALL)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
        mvc.perform(get(API + "/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongApp)).andExpect(status().isUnauthorized());
        // a spoofed tenant header is ignored: the tenant comes only from the verified token
        Order mine = retail(A, "CREATED"); retail(B, "CREATED");
        MvcResult r = mvc.perform(get(API + "/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A)).header("X-Tenant-ID", B.toString())).andExpect(status().isOk()).andReturn();
        JsonNode page = json.readTree(r.getResponse().getContentAsString());
        assertEquals(1, page.get("totalElements").asInt());
        assertEquals(mine.getId(), page.get("content").get(0).get("id").asLong());
        call(get(API + "/orders"), token(A, 3, List.of("order:export"), null), null, 403);
        call(get(API + "/audit"), without(A, "order:audit:view"), null, 403);
        call(get(API + "/reports/summary"), without(A, "order:report:view"), null, 403);
        call(get(API + "/orders/export"), without(A, "order:export"), null, 403);
    }

    @Test @DisplayName("tenant B cannot see or change tenant A orders through any endpoint")
    void tenantIsolation() throws Exception {
        Order a = retail(A, "CREATED");
        Order b = retail(B, "CREATED");
        String tb = admin(B);
        call(get(API + "/orders/" + a.getId()), tb, null, 404);
        call(get(API + "/orders/" + a.getId() + "/timeline"), tb, null, 404);
        call(get(API + "/orders/" + a.getId() + "/notes"), tb, null, 404);
        call(post(API + "/orders/" + a.getId() + "/transition"), tb, Map.of("to", "CONFIRMED"), 404, key());
        call(post(API + "/orders/" + a.getId() + "/hold"), tb, Map.of("reason", "x"), 404, key());
        call(post(API + "/orders/" + a.getId() + "/cancel"), tb, Map.of("reason", "x"), 404, key());
        call(post(API + "/orders/" + a.getId() + "/notes"), tb, Map.of("body", "hi"), 404);
        call(post(API + "/orders/" + a.getId() + "/assign"), tb, Map.of("operatorId", 5), 404);
        call(get(API + "/orders/" + a.getId() + "/fulfillment"), tb, null, 404);
        call(post(API + "/orders/" + a.getId() + "/fulfillments"), tb, Map.of("locationCode", "W1", "lines", List.of()), 404, key());
        assertEquals("CREATED", statusOf(a.getId()));
        JsonNode list = call(get(API + "/orders"), tb, null, 200);
        assertEquals(1, list.get("totalElements").asInt());
        assertEquals(b.getId(), list.get("content").get(0).get("id").asLong());
        assertEquals(0, call(get(API + "/orders?q=" + AdminSupport.orderNumber(a.getId())), tb, null, 200).get("totalElements").asInt());
        // csv export, bulk preview and audit never include A's orders
        MvcResult csv = mvc.perform(get(API + "/orders/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + tb)).andExpect(status().isOk()).andReturn();
        assertFalse(csv.getResponse().getContentAsString().contains(AdminSupport.orderNumber(a.getId())));
        JsonNode job = call(post(API + "/bulk/preview"), tb, Map.of("action", "ADD_NOTE", "orderIds", List.of(a.getId(), b.getId()), "params", Map.of("note", "hello")), 200);
        assertEquals(1, job.get("eligibleCount").asInt());
        assertEquals("Order not found", job.get("items").get(0).get("message").asText());
        call(get(API + "/audit?orderId=" + a.getId()), tb, null, 200);
        assertEquals(0, call(get(API + "/audit?orderId=" + a.getId()), tb, null, 200).size());
        // exceptions and dashboard are tenant-scoped
        retail(A, "PAYMENT_FAILED");
        call(post(API + "/exceptions/scan"), admin(A), null, 200);
        assertEquals(0, call(get(API + "/exceptions"), tb, null, 200).get("totalElements").asInt());
        assertEquals(1, call(get(API + "/dashboard"), tb, null, 200).get("ordersToday").asInt());
        // saved views are per user and tenant
        call(post(API + "/saved-views"), admin(A), Map.of("name", "Mine", "filters", Map.of("view", "NEW")), 200);
        assertEquals(0, call(get(API + "/saved-views"), tb, null, 200).size());
    }

    @Test @DisplayName("store-level authorisation: callers without view-all-stores see only the stores in their token")
    void storeScope() throws Exception {
        Order main = order(A, "main", "RETAIL", "CREATED", "10.00", "Zed"), north = order(A, "north", "RETAIL", "CREATED", "20.00", "Yan");
        String northOnly = token(A, 9, List.of("order:view", "order:process", "order:customer:view"), List.of("north"));
        JsonNode page = call(get(API + "/orders"), northOnly, null, 200);
        assertEquals(1, page.get("totalElements").asInt());
        assertEquals(north.getId(), page.get("content").get(0).get("id").asLong());
        call(get(API + "/orders/" + main.getId()), northOnly, null, 404);
        call(post(API + "/orders/" + main.getId() + "/assign"), northOnly, Map.of("operatorId", 3), 404);
        assertEquals(0, call(get(API + "/orders"), token(A, 10, List.of("order:view"), null), null, 200).get("totalElements").asInt()); // no stores at all -> nothing (fail closed)
        assertEquals(2, call(get(API + "/orders"), admin(A), null, 200).get("totalElements").asInt());
    }

    @Test @DisplayName("customer details need their own permission and are neither shown nor searchable without it")
    void customerPrivacy() throws Exception {
        Order o = retail(A, "CREATED");
        String noPii = without(A, "order:customer:view");
        JsonNode row = call(get(API + "/orders"), noPii, null, 200).get("content").get(0);
        assertTrue(row.get("customerHidden").asBoolean());
        assertEquals("Hidden", row.at("/customer/name").asText());
        assertEquals(0, call(get(API + "/orders?q=alice"), noPii, null, 200).get("totalElements").asInt());
        assertEquals(0, call(get(API + "/orders?q=alice.smith@example.com"), noPii, null, 200).get("totalElements").asInt());
        assertEquals(1, call(get(API + "/orders?q=" + AdminSupport.orderNumber(o.getId())), noPii, null, 200).get("totalElements").asInt());
        JsonNode detail = call(get(API + "/orders/" + o.getId()), noPii, null, 200);
        assertTrue(detail.at("/customer/hidden").asBoolean());
        assertTrue(detail.get("customer").path("email").isMissingNode());
        assertEquals(1, call(get(API + "/orders?q=alice"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(1, call(get(API + "/orders?q=alice.smith"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals("Alice Smith", call(get(API + "/orders/" + o.getId()), admin(A), null, 200).at("/customer/name").asText());
        String csvNoPii = mvc.perform(get(API + "/orders/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + noPii)).andReturn().getResponse().getContentAsString();
        assertFalse(csvNoPii.contains("Alice") || csvNoPii.contains("customerName"));
        String csv = mvc.perform(get(API + "/orders/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("customerName") && csv.contains("Alice Smith"));
    }

    // ---- listing ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("server-side pagination, multi-column sorting, filters and menu views")
    void listing() throws Exception {
        for (int i = 0; i < 7; i++) order(A, i % 2 == 0 ? "main" : "north", "RETAIL", i < 3 ? "CREATED" : "PROCESSING", String.valueOf(10 + i), "Cust " + i);
        order(A, "main", "RESTAURANT", "RECEIVED", "99.00", "Hungry");
        jdbc.update("UPDATE orders SET payment_status='PAID' WHERE total_amount > 14 AND total_amount < 100");
        JsonNode p0 = call(get(API + "/orders?size=3&page=0&sort=totalAmount:asc"), admin(A), null, 200);
        assertEquals(8, p0.get("totalElements").asInt());
        assertEquals(3, p0.get("totalPages").asInt());
        assertEquals(3, p0.get("content").size());
        assertEquals(0, new BigDecimal("10.00").compareTo(p0.get("content").get(0).get("totalAmount").decimalValue()));
        JsonNode p2 = call(get(API + "/orders?size=3&page=2&sort=totalAmount:asc"), admin(A), null, 200);
        assertEquals(2, p2.get("content").size());
        JsonNode multi = call(get(API + "/orders?sort=status:asc&sort=totalAmount:desc&size=50"), admin(A), null, 200);
        assertEquals("CREATED", multi.get("content").get(0).get("status").asText());
        assertEquals(0, new BigDecimal("12.00").compareTo(multi.get("content").get(0).get("totalAmount").decimalValue()));
        assertEquals(3, call(get(API + "/orders?view=NEW"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(1, call(get(API + "/orders?view=PENDING_CONFIRMATION"), admin(A), null, 200).get("totalElements").asInt()); // restaurant RECEIVED
        assertEquals(4, call(get(API + "/orders?view=PROCESSING"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(1, call(get(API + "/orders?orderType=RESTAURANT"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(3, call(get(API + "/orders?storeId=north"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(3, call(get(API + "/orders?storeId=north&orderType=RETAIL"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(3, call(get(API + "/orders?paymentStatus=PAID"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(2, call(get(API + "/orders?minAmount=11&maxAmount=12.5"), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(8, call(get(API + "/orders?from=" + java.time.LocalDate.now() + "&to=" + java.time.LocalDate.now()), admin(A), null, 200).get("totalElements").asInt());
        assertEquals(0, call(get(API + "/orders?from=" + java.time.LocalDate.now().plusDays(1)), admin(A), null, 200).get("totalElements").asInt());
        JsonNode counts = call(get(API + "/view-counts"), admin(A), null, 200);
        assertEquals(3, counts.get("NEW").asInt());
        // validation
        call(get(API + "/orders?sort=password:asc"), admin(A), null, 400);
        call(get(API + "/orders?sort=id;drop table orders"), admin(A), null, 400);
        call(get(API + "/orders?size=1000"), admin(A), null, 400);
        call(get(API + "/orders?page=-1"), admin(A), null, 400);
        call(get(API + "/orders?view=EVERYTHING"), admin(A), null, 400);
        call(get(API + "/orders?paymentStatus=MAYBE"), admin(A), null, 400);
        call(get(API + "/orders?from=2026-10-31&to=2026-10-01"), admin(A), null, 400);
        // a wildcard in the search box is data, not a pattern
        assertEquals(0, call(get(API + "/orders?q=%25"), admin(A), null, 200).get("totalElements").asInt());
    }

    @Test @DisplayName("SLA breach filter and indicator use the tenant's thresholds")
    void slaFilter() throws Exception {
        Order late = retail(A, "CREATED"), fresh = retail(A, "CREATED");
        jdbc.update("UPDATE orders SET status_changed_at=? WHERE id=?", OffsetDateTime.now().minusMinutes(90), late.getId());
        JsonNode breached = call(get(API + "/orders?slaBreached=true"), admin(A), null, 200);
        assertEquals(1, breached.get("totalElements").asInt());
        assertEquals(late.getId(), breached.get("content").get(0).get("id").asLong());
        assertEquals("BREACHED", breached.at("/content/0/sla/indicator").asText());
        assertEquals(1, call(get(API + "/orders?slaBreached=false"), admin(A), null, 200).get("totalElements").asInt());
        call(put(API + "/settings"), admin(A), Map.of("confirmSlaMinutes", 240), 200);
        assertEquals(0, call(get(API + "/orders?slaBreached=true"), admin(A), null, 200).get("totalElements").asInt());
        call(put(API + "/settings"), admin(A), Map.of("confirmSlaMinutes", 0), 400);
        call(put(API + "/settings"), admin(A), Map.of("maxBulkSize", 5000), 400);
        call(put(API + "/settings"), without(A, "order:settings"), Map.of("confirmSlaMinutes", 10), 403);
        assertEquals(240, call(get(API + "/settings"), admin(A), null, 200).get("confirmSlaMinutes").asInt());
        assertEquals(30, call(get(API + "/settings"), admin(B), null, 200).get("confirmSlaMinutes").asInt()); // other tenant keeps defaults
        assertNotNull(fresh);
    }

    // ---- state machine over the API ---------------------------------------------------------------------------------

    @Test @DisplayName("valid transitions succeed, invalid ones are refused with the allowed list, and every change is in the history")
    void stateMachine() throws Exception {
        Order o = retail(A, "CREATED");
        walk(A, o.getId(), "CONFIRMED", "PROCESSING", "READY_FOR_FULFILLMENT");
        assertEquals("READY_FOR_FULFILLMENT", statusOf(o.getId()));
        JsonNode bad = transition(A, o.getId(), "DELIVERED", null, 409);
        assertTrue(bad.get("error").asText().contains("Allowed next"));
        assertEquals("READY_FOR_FULFILLMENT", statusOf(o.getId()));
        transition(A, o.getId(), "FULFILLED", null, 409);   // fulfillment-driven
        transition(A, o.getId(), "PARTIALLY_FULFILLED", null, 409);
        transition(A, o.getId(), "PAYMENT_CONFIRMED", null, 409); // payment-owned
        transition(A, o.getId(), "REFUNDED", null, 409);
        transition(A, o.getId(), "MADE_UP_STATUS", null, 400);
        call(post(API + "/orders/" + o.getId() + "/transition"), admin(A), Map.of("to", ""), 400, key());
        transition(A, o.getId(), "CANCELLED", null, 400); // reason required
        JsonNode meta = call(get(API + "/orders/" + o.getId()), admin(A), null, 200);
        Set<String> allowed = new HashSet<>();
        meta.get("allowedTransitions").forEach(t -> { if (t.get("allowed").asBoolean()) allowed.add(t.get("to").asText()); });
        assertTrue(allowed.containsAll(Set.of("SHIPPED", "ON_HOLD", "CANCELLATION_REQUESTED", "CANCELLED")));
        assertFalse(allowed.contains("FULFILLED"));
        JsonNode timeline = call(get(API + "/orders/" + o.getId() + "/timeline"), admin(A), null, 200);
        assertTrue(timeline.get("events").size() >= 4);
        assertEquals("ORDER_CREATED", timeline.get("events").get(0).get("type").asText());
        JsonNode audit = call(get(API + "/audit?orderId=" + o.getId()), admin(A), null, 200);
        assertEquals(3, audit.size());
        assertEquals("READY_FOR_FULFILLMENT", audit.get(0).get("toStatus").asText());
        assertEquals("PROCESSING", audit.get(0).get("fromStatus").asText());
        assertEquals(1L, audit.get(0).get("actorId").asLong());
        assertFalse(audit.get(0).get("correlationId").asText().isBlank());
        assertNotNull(o);
    }

    @Test @DisplayName("each target status needs its own permission, enforced by the backend")
    void transitionPermissions() throws Exception {
        Order o = retail(A, "CREATED");
        call(post(API + "/orders/" + o.getId() + "/transition"), without(A, "order:confirm"), Map.of("to", "CONFIRMED"), 403, key());
        call(post(API + "/orders/" + o.getId() + "/hold"), without(A, "order:hold"), Map.of("reason", "x"), 403, key());
        call(post(API + "/orders/" + o.getId() + "/cancel"), without(A, "order:cancel"), Map.of("reason", "x"), 403, key());
        call(post(API + "/orders/" + o.getId() + "/transition"), token(A, 4, List.of("order:view", "order:view-all-stores"), null), Map.of("to", "CONFIRMED"), 403, key());
        assertEquals("CREATED", statusOf(o.getId()));
        transition(A, o.getId(), "CONFIRMED", null, 200);
        call(post(API + "/orders/" + o.getId() + "/transition"), without(A, "order:process"), Map.of("to", "PROCESSING"), 403, key());
        JsonNode viewer = call(get(API + "/orders/" + o.getId()), without(A, "order:process"), null, 200);
        viewer.get("allowedTransitions").forEach(t -> { if (t.get("to").asText().equals("PROCESSING")) assertFalse(t.get("allowed").asBoolean()); });
        call(post(API + "/orders/" + o.getId() + "/notes"), without(A, "order:note:create"), Map.of("body", "x"), 403);
        call(post(API + "/orders/" + o.getId() + "/assign"), without(A, "order:process"), Map.of("operatorId", 3), 403);
    }

    @Test @DisplayName("hold and release return to the previous status; cancellation requests can be withdrawn or confirmed")
    void holdAndCancel() throws Exception {
        Order o = retail(A, "CREATED");
        walk(A, o.getId(), "CONFIRMED", "PROCESSING");
        call(post(API + "/orders/" + o.getId() + "/hold"), admin(A), Map.of("reason", ""), 400, key());
        JsonNode held = call(post(API + "/orders/" + o.getId() + "/hold"), admin(A), Map.of("reason", "Address check"), 200, key());
        assertEquals("ON_HOLD", held.get("status").asText());
        assertEquals("Address check", orders.findById(o.getId()).orElseThrow().getHoldReason());
        transition(A, o.getId(), "PROCESSING", null, 409); // cannot jump out of hold except through release
        call(post(API + "/orders/" + o.getId() + "/release"), without(A, "order:release"), Map.of(), 403, key());
        assertEquals("PROCESSING", call(post(API + "/orders/" + o.getId() + "/release"), admin(A), Map.of("reason", "Verified"), 200, key()).get("status").asText());
        call(post(API + "/orders/" + o.getId() + "/release"), admin(A), Map.of(), 409, key()); // not on hold any more

        assertEquals("CANCELLATION_REQUESTED", call(post(API + "/orders/" + o.getId() + "/request-cancellation"), admin(A), Map.of("reason", "Customer asked"), 200, key()).get("status").asText());
        assertEquals("PROCESSING", call(post(API + "/orders/" + o.getId() + "/release"), admin(A), Map.of("reason", "Customer changed their mind"), 200, key()).get("status").asText());
        call(post(API + "/orders/" + o.getId() + "/request-cancellation"), admin(A), Map.of("reason", "Again"), 200, key());
        JsonNode cancelled = call(post(API + "/orders/" + o.getId() + "/cancel"), admin(A), Map.of("reason", "Confirmed with customer"), 200, key());
        assertEquals("CANCELLED", cancelled.get("status").asText());
        assertNotNull(orders.findById(o.getId()).orElseThrow().getCancelledAt());
        call(post(API + "/orders/" + o.getId() + "/cancel"), admin(A), Map.of("reason", "again"), 409, key());
        assertEquals(List.of("CREATED", "CONFIRMED", "PROCESSING", "ON_HOLD", "PROCESSING", "CANCELLATION_REQUESTED", "PROCESSING", "CANCELLATION_REQUESTED", "CANCELLED").size(), 1 + call(get(API + "/audit?orderId=" + o.getId()), admin(A), null, 200).size());
    }

    @Test @DisplayName("sensitive operations are idempotent: replays return the first result, conflicting reuse is refused")
    void idempotency() throws Exception {
        Order o = retail(A, "CREATED");
        String k = "idem-key-0001";
        JsonNode first = call(post(API + "/orders/" + o.getId() + "/transition"), admin(A), Map.of("to", "CONFIRMED"), 200, k);
        JsonNode replay = call(post(API + "/orders/" + o.getId() + "/transition"), admin(A), Map.of("to", "CONFIRMED"), 200, k);
        assertEquals(first.get("version").asLong(), replay.get("version").asLong());
        assertTrue(replay.get("idempotentReplay").asBoolean());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='CONFIRMED'", Integer.class, o.getId()));
        call(post(API + "/orders/" + o.getId() + "/transition"), admin(A), Map.of("to", "PROCESSING"), 422, k); // same key, different request
        call(post(API + "/orders/" + o.getId() + "/transition"), admin(A), Map.of("to", "PROCESSING"), 400, null); // key required
        call(post(API + "/orders/" + o.getId() + "/transition"), admin(A), Map.of("to", "PROCESSING"), 400, "short");
        assertEquals("CONFIRMED", statusOf(o.getId()));
        // the same key used by another tenant is independent
        Order other = retail(B, "CREATED");
        call(post(API + "/orders/" + other.getId() + "/transition"), admin(B), Map.of("to", "CONFIRMED"), 200, k);
    }

    @Test @DisplayName("optimistic locking: a stale expected version is refused; concurrent holds produce exactly one winner")
    void concurrency() throws Exception {
        Order o = retail(A, "CREATED");
        walk(A, o.getId(), "CONFIRMED");
        long version = orders.findById(o.getId()).orElseThrow().getVersion();
        call(post(API + "/orders/" + o.getId() + "/transition"), admin(A), Map.of("to", "PROCESSING", "expectedVersion", version - 1), 409, key());
        call(post(API + "/orders/" + o.getId() + "/transition"), admin(A), Map.of("to", "PROCESSING", "expectedVersion", version), 200, key());

        Order race = retail(A, "CONFIRMED");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), conflict = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final String k = "race-key-" + i + "-" + System.nanoTime();
            fs.add(pool.submit(() -> {
                try {
                    go.await();
                    int s = mvc.perform(post(API + "/orders/" + race.getId() + "/hold").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A)).header("Idempotency-Key", k)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"race\"}")).andReturn().getResponse().getStatus();
                    if (s == 200) ok.incrementAndGet(); else if (s == 409) conflict.incrementAndGet();
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        go.countDown();
        for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(1, ok.get(), "exactly one hold may win");
        assertEquals(7, conflict.get());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='ON_HOLD'", Integer.class, race.getId()));

        // the same idempotency key sent concurrently runs once
        Order dup = retail(A, "CONFIRMED");
        ExecutorService pool2 = Executors.newFixedThreadPool(6);
        CountDownLatch go2 = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) results.add(pool2.submit(() -> {
            go2.await();
            return mvc.perform(post(API + "/orders/" + dup.getId() + "/transition").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A)).header("Idempotency-Key", "same-key-for-all")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"PROCESSING\"}")).andReturn().getResponse().getStatus();
        }));
        go2.countDown();
        for (Future<Integer> f : results) assertEquals(200, f.get(60, TimeUnit.SECONDS));
        pool2.shutdown();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='PROCESSING'", Integer.class, dup.getId()));
    }

    // ---- payment and refunds (owned by other domains) -------------------------------------------------------------

    private JsonNode internal(String path, UUID tenant, Object body, int status) throws Exception {
        MvcResult r = mvc.perform(post("/internal/order-events/" + path).header("X-Service-Token", "test-service-token").header("X-Tenant-Id", tenant.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().is(status)).andReturn();
        String c = r.getResponse().getContentAsString();
        return c.startsWith("{") ? json.readTree(c) : null;
    }

    @Test @DisplayName("payment status comes only from the Payment callback (idempotent); the tenant can require payment before processing")
    void paymentFlow() throws Exception {
        Order o = retail(A, "CREATED");
        call(put(API + "/settings"), admin(A), Map.of("requirePaymentBeforeProcessing", true), 200);
        walk(A, o.getId(), "CONFIRMED");
        JsonNode blocked = call(get(API + "/orders/" + o.getId()), admin(A), null, 200);
        blocked.get("allowedTransitions").forEach(t -> { if (t.get("to").asText().equals("PROCESSING")) { assertFalse(t.get("allowed").asBoolean()); assertTrue(t.get("blockedReason").asText().contains("Payment")); } });
        transition(A, o.getId(), "PROCESSING", null, 409);
        assertEquals("UNKNOWN", orders.findById(o.getId()).orElseThrow().getPaymentStatus());

        internal("payment", A, Map.of("eventId", "pay-1", "orderId", o.getId(), "status", "PAID", "method", "CARD", "reference", "TX-9"), 200);
        Order paid = orders.findById(o.getId()).orElseThrow();
        assertEquals("PAID", paid.getPaymentStatus());
        assertEquals("TX-9", paid.getPaymentReference());
        assertEquals("CONFIRMED", paid.getStatus()); // PAYMENT_CONFIRMED is not reachable from CONFIRMED; payment status still recorded
        transition(A, o.getId(), "PROCESSING", null, 200);
        JsonNode dup = internal("payment", A, Map.of("eventId", "pay-1", "orderId", o.getId(), "status", "PAID", "method", "CARD", "reference", "TX-9"), 200);
        assertTrue(dup.get("duplicate").asBoolean());
        internal("payment", A, Map.of("eventId", "pay-2", "orderId", o.getId(), "status", "FAILED"), 409); // late failure after PAID is ignored
        JsonNode detail = call(get(API + "/orders/" + o.getId()), admin(A), null, 200);
        assertEquals("PAID", detail.at("/payment/status").asText());
        assertEquals(1, detail.at("/payment/attempts").asInt());

        Order fresh = retail(A, "CREATED");
        internal("payment", A, Map.of("eventId", "pay-3", "orderId", fresh.getId(), "status", "PENDING"), 200);
        assertEquals("PAYMENT_PENDING", statusOf(fresh.getId()));
        internal("payment", A, Map.of("eventId", "pay-4", "orderId", fresh.getId(), "status", "PAID", "method", "UPI", "reference", "R1"), 200);
        assertEquals("PAYMENT_CONFIRMED", statusOf(fresh.getId()));
        Order failing = retail(A, "PAYMENT_PENDING");
        internal("payment", A, Map.of("eventId", "pay-5", "orderId", failing.getId(), "status", "FAILED"), 200);
        assertEquals("PAYMENT_FAILED", statusOf(failing.getId()));

        // wrong tenant, bad token, bad status
        internal("payment", B, Map.of("eventId", "pay-6", "orderId", fresh.getId(), "status", "PAID"), 404);
        mvc.perform(post("/internal/order-events/payment").header("X-Tenant-Id", A.toString()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/order-events/payment").header("X-Service-Token", "nope").header("X-Tenant-Id", A.toString()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        internal("payment", A, Map.of("eventId", "pay-7", "orderId", fresh.getId(), "status", "MAYBE"), 400);
    }

    @Test @DisplayName("an order is REFUNDED only after the Payment/Accounting callback confirms it; an administrator cannot set it")
    void refundFlow() throws Exception {
        Order o = retail(A, "CREATED");
        internal("payment", A, Map.of("eventId", "r-pay", "orderId", o.getId(), "status", "PAID", "method", "CARD", "reference", "T1"), 200);
        walk(A, o.getId(), "CONFIRMED");
        call(post(API + "/orders/" + o.getId() + "/cancel"), admin(A), Map.of("reason", "Out of stock"), 200, key());
        Order cancelled = orders.findById(o.getId()).orElseThrow();
        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("PENDING", cancelled.getRefundStatus()); // refund requested, not completed
        transition(A, o.getId(), "REFUNDED", "please", 409);
        transition(A, o.getId(), "REFUND_PENDING", "please", 409);
        assertEquals("CANCELLED", statusOf(o.getId()));
        internal("refund", A, Map.of("eventId", "ref-1", "orderId", o.getId(), "status", "FAILED", "reference", "bank declined"), 200);
        assertEquals("FAILED", orders.findById(o.getId()).orElseThrow().getRefundStatus());
        assertEquals("CANCELLED", statusOf(o.getId()));
        internal("refund", A, Map.of("eventId", "ref-2", "orderId", o.getId(), "status", "COMPLETED", "reference", "RF-77"), 200);
        Order refunded = orders.findById(o.getId()).orElseThrow();
        assertEquals("REFUNDED", refunded.getStatus());
        assertEquals("COMPLETED", refunded.getRefundStatus());
        assertEquals("RF-77", refunded.getRefundReference());
        assertTrue(internal("refund", A, Map.of("eventId", "ref-2", "orderId", o.getId(), "status", "COMPLETED", "reference", "RF-77"), 200).get("duplicate").asBoolean());
        internal("refund", A, Map.of("eventId", "ref-3", "orderId", retail(A, "PROCESSING").getId(), "status", "COMPLETED", "reference", "x"), 409); // nothing to refund
        internal("refund", B, Map.of("eventId", "ref-4", "orderId", o.getId(), "status", "COMPLETED", "reference", "x"), 404);
        JsonNode payment = call(get(API + "/orders/" + o.getId()), admin(A), null, 200).get("payment");
        assertEquals("COMPLETED", payment.get("refundStatus").asText());
        assertEquals("REFUNDED", payment.get("status").asText());
        long refundEvents = 0;
        for (JsonNode h : payment.get("history")) if (h.get("action").asText().startsWith("REFUND")) refundEvents++;
        assertTrue(refundEvents >= 3);
    }

    // ---- restaurant ------------------------------------------------------------------------------------------------

    @Test @DisplayName("restaurant orders follow their own kitchen workflow and are kept out of warehouse flows")
    void restaurantWorkflow() throws Exception {
        Order o = order(A, "main", "RESTAURANT", "RECEIVED", "30.00", "Dina");
        call(post(API + "/orders/" + o.getId() + "/restaurant/accept"), admin(A), Map.of(), 400, key()); // prep time required
        call(post(API + "/orders/" + o.getId() + "/restaurant/accept"), admin(A), Map.of("prepMinutes", 500), 400, key());
        call(post(API + "/orders/" + o.getId() + "/restaurant/ready"), admin(A), Map.of(), 409, key()); // cannot skip ahead
        call(post(API + "/orders/" + o.getId() + "/restaurant/accept"), admin(A), Map.of("prepMinutes", 25), 200, key());
        assertEquals(25, orders.findById(o.getId()).orElseThrow().getPrepMinutes());
        call(post(API + "/orders/" + o.getId() + "/restaurant/start"), admin(A), Map.of(), 200, key());
        call(post(API + "/orders/" + o.getId() + "/restaurant/delay"), admin(A), Map.of(), 400, key()); // reason required
        call(post(API + "/orders/" + o.getId() + "/restaurant/delay"), admin(A), Map.of("reason", "Rush hour", "prepMinutes", 40), 200, key());
        assertEquals(40, orders.findById(o.getId()).orElseThrow().getPrepMinutes());
        call(post(API + "/orders/" + o.getId() + "/restaurant/start"), admin(A), Map.of(), 200, key());
        call(post(API + "/orders/" + o.getId() + "/restaurant/ready"), admin(A), Map.of(), 200, key());
        call(post(API + "/orders/" + o.getId() + "/restaurant/picked-up"), admin(A), Map.of(), 200, key());
        assertEquals("PICKED_UP", statusOf(o.getId()));
        // retail statuses are not available to a restaurant order, and the warehouse fulfillment tab does not apply
        transition(A, o.getId(), "SHIPPED", null, 400);   // not a restaurant status at all
        transition(A, o.getId(), "PROCESSING", null, 400);
        JsonNode overview = call(get(API + "/orders/" + o.getId() + "/fulfillment"), admin(A), null, 200);
        assertFalse(overview.get("applicable").asBoolean());
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "W1", "lines", List.of(Map.of("orderItemId", 1, "quantity", 1))), 409, key());

        Order rejected = order(A, "main", "RESTAURANT", "RECEIVED", "12.00", "Eli");
        call(post(API + "/orders/" + rejected.getId() + "/restaurant/reject"), admin(A), Map.of(), 400, key());
        call(post(API + "/orders/" + rejected.getId() + "/restaurant/reject"), admin(A), Map.of("reason", "Kitchen closed"), 200, key());
        Order r = orders.findById(rejected.getId()).orElseThrow();
        assertEquals("REJECTED", r.getStatus());
        assertEquals("Kitchen closed", r.getRejectionReason());
        transition(A, rejected.getId(), "ACCEPTED", null, 409); // terminal
        // retail order cannot use restaurant actions
        call(post(API + "/orders/" + retail(A, "CREATED").getId() + "/restaurant/accept"), admin(A), Map.of("prepMinutes", 10), 409, key());
        // restaurant views and the restaurant accept SLA (5 minutes)
        Order late = order(A, "main", "RESTAURANT", "RECEIVED", "9.00", "Fay");
        jdbc.update("UPDATE orders SET status_changed_at=? WHERE id=?", OffsetDateTime.now().minusMinutes(10), late.getId());
        assertEquals("BREACHED", call(get(API + "/orders?q=" + AdminSupport.orderNumber(late.getId())), admin(A), null, 200).at("/content/0/sla/indicator").asText());
    }

    // ---- fulfillment ------------------------------------------------------------------------------------------------

    private Order twoLines(UUID tenant) {
        Order o = retail(tenant, "READY_FOR_FULFILLMENT"); // item 1: 5 x Widget
        OrderItem second = new OrderItem(); second.setProductId(2L); second.setProductName("Gadget"); second.setPrice(new BigDecimal("5.00")); second.setQuantity(4);
        o.addItem(second);
        return orders.save(o);
    }
    private long itemId(Order o, int index) { return orders.findById(o.getId()).orElseThrow().getItems().get(index).getId(); }

    @Test @DisplayName("allocation can never exceed the ordered quantity; partial fulfillment and split shipments move the order forward")
    void fulfillment() throws Exception {
        Order o = twoLines(A);
        long widget = itemId(o, 0), gadget = itemId(o, 1);
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-1", "lines", List.of(Map.of("orderItemId", widget, "quantity", 6))), 409, key());
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-1", "lines", List.of(Map.of("orderItemId", widget, "quantity", 0))), 400, key());
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-1", "lines", List.of(Map.of("orderItemId", 999999, "quantity", 1))), 400, key());
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "bad code!", "lines", List.of(Map.of("orderItemId", widget, "quantity", 1))), 400, key());
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-1", "lines", List.of(Map.of("orderItemId", widget, "quantity", 1), Map.of("orderItemId", widget, "quantity", 1))), 400, key());
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), without(A, "order:process"), Map.of("locationCode", "WH-1", "lines", List.of(Map.of("orderItemId", widget, "quantity", 1))), 403, key());

        long f1 = call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-1", "locationName", "Warehouse 1", "lines", List.of(Map.of("orderItemId", widget, "quantity", 3))), 201, key()).get("fulfillmentId").asLong();
        long f2 = call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "STORE-7", "lines", List.of(Map.of("orderItemId", widget, "quantity", 2), Map.of("orderItemId", gadget, "quantity", 1))), 201, key()).get("fulfillmentId").asLong();
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-1", "lines", List.of(Map.of("orderItemId", widget, "quantity", 1))), 409, key()); // widget fully allocated
        JsonNode overview = call(get(API + "/orders/" + o.getId() + "/fulfillment"), admin(A), null, 200);
        assertEquals("ALLOCATED", overview.get("fulfillmentStatus").asText());
        assertEquals(0, overview.at("/items/0/unallocated").asInt());
        assertEquals(3, overview.at("/items/1/unallocated").asInt());
        assertEquals(2, overview.get("fulfillments").size());
        call(get(API + "/orders/" + o.getId() + "/fulfillment"), token(A, 5, List.of("order:view"), null), null, 403);

        call(post(API + "/fulfillments/" + f1 + "/ship"), admin(A), Map.of("carrier", "", "trackingReference", "T1"), 400, key()); // carrier required to ship
        call(post(API + "/fulfillments/" + f1 + "/ship"), admin(A), Map.of("carrier", "DHL", "trackingReference", "<script>"), 400, key());
        call(post(API + "/fulfillments/" + f1 + "/packing"), admin(A), Map.of("packingStatus", "PACKED"), 200);
        String shipKey = key();
        JsonNode shipped = call(post(API + "/fulfillments/" + f1 + "/ship"), admin(A), Map.of("carrier", "DHL", "trackingReference", "DHL-123"), 200, shipKey);
        assertEquals("PARTIALLY_FULFILLED", shipped.get("orderStatus").asText());
        assertEquals("PARTIAL", shipped.get("fulfillmentStatus").asText());
        assertTrue(call(post(API + "/fulfillments/" + f1 + "/ship"), admin(A), Map.of("carrier", "DHL", "trackingReference", "DHL-123"), 200, shipKey).get("idempotentReplay").asBoolean());
        call(post(API + "/fulfillments/" + f1 + "/ship"), admin(A), Map.of("carrier", "DHL", "trackingReference", "DHL-123"), 409, key()); // already shipped
        call(post(API + "/fulfillments/" + f1 + "/cancel"), admin(A), null, 409);
        assertEquals(3, orders.findById(o.getId()).orElseThrow().getItems().get(0).getFulfilledQuantity());
        // a cancellation attempt is refused while goods are already out
        call(post(API + "/orders/" + o.getId() + "/cancel"), admin(A), Map.of("reason", "too late"), 409, key());

        JsonNode second = call(post(API + "/fulfillments/" + f2 + "/ship"), admin(A), Map.of("carrier", "FedEx", "trackingReference", "FX-9"), 200, key());
        assertEquals("PARTIALLY_FULFILLED", second.get("orderStatus").asText()); // 3 gadgets are still unallocated
        long f3 = call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-1", "lines", List.of(Map.of("orderItemId", gadget, "quantity", 3))), 201, key()).get("fulfillmentId").asLong();
        JsonNode last = call(post(API + "/fulfillments/" + f3 + "/ship"), admin(A), Map.of("carrier", "DHL", "trackingReference", "DHL-456"), 200, key());
        assertEquals("SHIPPED", last.get("orderStatus").asText());
        assertEquals("FULFILLED", last.get("fulfillmentStatus").asText());
        assertEquals("DHL-456", orders.findById(o.getId()).orElseThrow().getTrackingNumber());
    }

    @Test @DisplayName("fulfillment status reflects unallocated remainder; cancelling an unshipped fulfillment releases its allocation")
    void fulfillmentRelease() throws Exception {
        Order o = twoLines(A);
        long widget = itemId(o, 0), gadget = itemId(o, 1);
        long f1 = call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-1", "lines", List.of(Map.of("orderItemId", widget, "quantity", 5), Map.of("orderItemId", gadget, "quantity", 4))), 201, key()).get("fulfillmentId").asLong();
        assertEquals(0, call(get(API + "/orders/" + o.getId() + "/fulfillment"), admin(A), null, 200).at("/items/0/unallocated").asInt());
        JsonNode cancelled = call(post(API + "/fulfillments/" + f1 + "/cancel"), admin(A), null, 200);
        assertEquals("UNFULFILLED", cancelled.get("fulfillmentStatus").asText());
        JsonNode after = call(get(API + "/orders/" + o.getId() + "/fulfillment"), admin(A), null, 200);
        assertEquals(5, after.at("/items/0/unallocated").asInt());
        // multi-vendor: one fulfillment per vendor
        jdbc.update("UPDATE order_items SET vendor_id='V1' WHERE id=?", widget);
        jdbc.update("UPDATE order_items SET vendor_id='V2' WHERE id=?", gadget);
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-3", "lines", List.of(Map.of("orderItemId", gadget, "quantity", 1), Map.of("orderItemId", widget, "quantity", 1))), 400, key());
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-2", "lines", List.of(Map.of("orderItemId", widget, "quantity", 5))), 201, key());
        call(post(API + "/orders/" + o.getId() + "/fulfillments"), admin(A), Map.of("locationCode", "WH-3", "lines", List.of(Map.of("orderItemId", gadget, "quantity", 4))), 201, key());
        // cancelling the order releases remaining unshipped allocations
        call(post(API + "/orders/" + o.getId() + "/cancel"), admin(A), Map.of("reason", "Customer request"), 200, key());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_fulfillments WHERE order_id=? AND status IN ('ALLOCATED','PACKED')", Integer.class, o.getId()));
        assertEquals(0, orders.findById(o.getId()).orElseThrow().getItems().get(0).getAllocatedQuantity());
    }

    @Test @DisplayName("concurrent allocations of the same item never over-allocate")
    void allocationRace() throws Exception {
        Order o = retail(A, "READY_FOR_FULFILLMENT"); // 5 x Widget
        long widget = itemId(o, 0);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger(), refused = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int n = i;
            fs.add(pool.submit(() -> {
                try {
                    go.await();
                    int s = mvc.perform(post(API + "/orders/" + o.getId() + "/fulfillments").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A)).header("Idempotency-Key", "alloc-" + n + "-" + System.nanoTime())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"locationCode\":\"WH-" + n + "\",\"lines\":[{\"orderItemId\":" + widget + ",\"quantity\":2}]}")).andReturn().getResponse().getStatus();
                    if (s == 201) created.incrementAndGet(); else if (s == 409) refused.incrementAndGet();
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        go.countDown();
        for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(2, created.get(), "5 units allow exactly two allocations of 2");
        assertEquals(8, refused.get());
        assertEquals(4, orders.findById(o.getId()).orElseThrow().getItems().get(0).getAllocatedQuantity());
        assertEquals(4, jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM order_fulfillment_items WHERE order_item_id=?", Integer.class, widget));
    }

    // ---- exceptions ------------------------------------------------------------------------------------------------

    @Test @DisplayName("exception centre: detection is idempotent, SLA-based, auto-resolves, and can be assigned, escalated and resolved")
    void exceptionCentre() throws Exception {
        Order failed = retail(A, "PAYMENT_FAILED");
        Order late = retail(A, "CREATED");
        jdbc.update("UPDATE orders SET status_changed_at=? WHERE id=?", OffsetDateTime.now().minusMinutes(100), late.getId());
        Order stuck = retail(A, "PROCESSING");
        jdbc.update("UPDATE orders SET status_changed_at=? WHERE id=?", OffsetDateTime.now().minusMinutes(600), stuck.getId());
        Order cr = retail(A, "CANCELLATION_REQUESTED");
        Order shortage = retail(A, "PENDING_INVENTORY");
        Order healthy = retail(A, "CONFIRMED");

        call(post(API + "/exceptions/scan"), without(A, "order:exception:manage"), null, 403);
        JsonNode scan = call(post(API + "/exceptions/scan"), admin(A), null, 200);
        assertEquals(5, scan.get("opened").asInt());
        assertEquals(0, call(post(API + "/exceptions/scan"), admin(A), null, 200).get("opened").asInt()); // idempotent
        JsonNode list = call(get(API + "/exceptions"), admin(A), null, 200);
        assertEquals(5, list.get("totalElements").asInt());
        Set<String> categories = new HashSet<>();
        list.get("content").forEach(e -> categories.add(e.get("category").asText()));
        assertEquals(Set.of("PAYMENT_FAILURE", "AWAITING_CONFIRMATION", "STUCK_PROCESSING", "CANCELLATION_REQUEST", "INVENTORY_SHORTAGE"), categories);
        assertEquals("HIGH", call(get(API + "/exceptions?category=PAYMENT_FAILURE"), admin(A), null, 200).at("/content/0/priority").asText());
        assertEquals(1, call(get(API + "/exceptions?category=STUCK_PROCESSING"), admin(A), null, 200).get("totalElements").asInt());
        assertNotNull(healthy);

        long paymentExc = call(get(API + "/exceptions?category=PAYMENT_FAILURE"), admin(A), null, 200).at("/content/0/id").asLong();
        call(post(API + "/exceptions/" + paymentExc + "/assign"), without(A, "order:exception:manage"), Map.of("assigneeId", 5), 403);
        call(post(API + "/exceptions/" + paymentExc + "/assign"), admin(A), Map.of(), 400);
        assertEquals("ASSIGNED", call(post(API + "/exceptions/" + paymentExc + "/assign"), admin(A), Map.of("assigneeId", 5, "note", "yours"), 200).get("status").asText());
        call(post(API + "/exceptions/" + paymentExc + "/escalate"), admin(A), Map.of(), 400);
        JsonNode esc = call(post(API + "/exceptions/" + paymentExc + "/escalate"), admin(A), Map.of("note", "Customer is VIP"), 200);
        assertEquals("ESCALATED", esc.get("status").asText());
        assertEquals("CRITICAL", esc.get("priority").asText());
        call(post(API + "/exceptions/" + paymentExc + "/notes"), admin(A), Map.of("note", "Called the bank"), 200);
        call(post(API + "/exceptions/" + paymentExc + "/resolve"), admin(A), Map.of(), 400);
        JsonNode resolved = call(post(API + "/exceptions/" + paymentExc + "/resolve"), admin(A), Map.of("note", "Customer paid by another method"), 200);
        assertEquals("RESOLVED", resolved.get("status").asText());
        List<String> actions = new ArrayList<>();
        resolved.get("events").forEach(e -> actions.add(e.get("action").asText()));
        assertEquals(List.of("OPENED", "ASSIGNED", "ESCALATED", "NOTE", "RESOLVED"), actions);
        call(post(API + "/exceptions/" + paymentExc + "/resolve"), admin(A), Map.of("note", "again"), 409);
        call(get(API + "/exceptions/" + paymentExc), admin(B), null, 404);
        call(post(API + "/exceptions/" + paymentExc + "/assign"), admin(B), Map.of("assigneeId", 9), 404);

        // the same order can raise the category again after resolution (the condition still holds)
        assertEquals(1, call(post(API + "/exceptions/scan"), admin(A), null, 200).get("opened").asInt());
        // auto-resolve when the condition clears
        jdbc.update("UPDATE orders SET status='CONFIRMED', status_changed_at=CURRENT_TIMESTAMP WHERE id=?", late.getId());
        JsonNode cleared = call(post(API + "/exceptions/scan"), admin(A), null, 200);
        assertTrue(cleared.get("resolved").asInt() >= 1);
        assertEquals(0, call(get(API + "/exceptions?category=AWAITING_CONFIRMATION"), admin(A), null, 200).get("totalElements").asInt());
        // tenant SLA thresholds drive detection
        call(put(API + "/settings"), admin(A), Map.of("processingSlaMinutes", 2000), 200);
        call(post(API + "/exceptions/scan"), admin(A), null, 200);
        assertEquals(0, call(get(API + "/exceptions?category=STUCK_PROCESSING"), admin(A), null, 200).get("totalElements").asInt());
        // repeated delivery failures escalate to CRITICAL
        Order courier = retail(A, "DELIVERY_FAILED");
        for (int i = 0; i < 2; i++) jdbc.update("INSERT INTO order_status_history(tenant_id, order_id, from_status, to_status, action, source, correlation_id) VALUES (?,?,?,?,?,?,?)", A, courier.getId(), "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "TRANSITION", "DELIVERY", "c" + i);
        call(post(API + "/exceptions/scan"), admin(A), null, 200);
        assertEquals("CRITICAL", call(get(API + "/exceptions?category=REPEATED_DELIVERY_FAILURE"), admin(A), null, 200).at("/content/0/priority").asText());
        assertNotNull(failed); assertNotNull(stuck); assertNotNull(cr); assertNotNull(shortage);
    }

    // ---- bulk ------------------------------------------------------------------------------------------------------

    @Test @DisplayName("bulk operations: preview first, eligible orders only, per-order validation, executes once, no arbitrary status changes")
    void bulk() throws Exception {
        Order a = retail(A, "CONFIRMED"), b = retail(A, "PROCESSING"), c = retail(A, "DELIVERED"), foreign = retail(B, "CONFIRMED");
        call(post(API + "/bulk/preview"), without(A, "order:bulk"), Map.of("action", "HOLD", "orderIds", List.of(a.getId()), "params", Map.of("reason", "x")), 403);
        call(post(API + "/bulk/preview"), without(A, "order:hold"), Map.of("action", "HOLD", "orderIds", List.of(a.getId()), "params", Map.of("reason", "x")), 403);
        call(post(API + "/bulk/preview"), admin(A), Map.of("action", "SET_STATUS", "orderIds", List.of(a.getId()), "params", Map.of("status", "DELIVERED")), 400);
        call(post(API + "/bulk/preview"), admin(A), Map.of("action", "HOLD", "orderIds", List.of(), "params", Map.of("reason", "x")), 400);
        call(post(API + "/bulk/preview"), admin(A), Map.of("action", "HOLD", "orderIds", List.of(a.getId()), "params", Map.of()), 400);
        call(post(API + "/bulk/preview"), admin(A), Map.of("action", "ADD_NOTE", "orderIds", List.of(a.getId()), "params", Map.of("note", "<script>x</script>")), 400);

        JsonNode preview = call(post(API + "/bulk/preview"), admin(A), Map.of("action", "HOLD", "orderIds", List.of(a.getId(), b.getId(), c.getId(), foreign.getId(), 987654), "params", Map.of("reason", "Audit")), 200);
        assertEquals(5, preview.get("totalCount").asInt());
        assertEquals(2, preview.get("eligibleCount").asInt());
        assertEquals("CONFIRMED", statusOf(a.getId())); // preview changes nothing
        long jobId = preview.get("id").asLong();
        call(post(API + "/bulk/" + jobId + "/execute"), admin(A), null, 400, null); // key required
        call(post(API + "/bulk/" + jobId + "/execute"), admin(B), null, 404, key());
        JsonNode done = call(post(API + "/bulk/" + jobId + "/execute"), admin(A), null, 200, key());
        assertEquals("COMPLETED", done.get("status").asText());
        assertEquals(2, done.get("successCount").asInt());
        assertEquals("ON_HOLD", statusOf(a.getId()));
        assertEquals("ON_HOLD", statusOf(b.getId()));
        assertEquals("DELIVERED", statusOf(c.getId()));
        assertEquals("CONFIRMED", statusOf(foreign.getId()));
        assertTrue(call(post(API + "/bulk/" + jobId + "/execute"), admin(A), null, 200, key()).get("alreadyExecuted").asBoolean());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='ON_HOLD'", Integer.class, a.getId()));

        // an order that changed between preview and execution is re-validated and skipped
        Order d = retail(A, "CONFIRMED"), e = retail(A, "CONFIRMED");
        long job2 = call(post(API + "/bulk/preview"), admin(A), Map.of("action", "REQUEST_CANCELLATION", "orderIds", List.of(d.getId(), e.getId()), "params", Map.of("reason", "Supplier recall")), 200).get("id").asLong();
        jdbc.update("UPDATE orders SET status='DELIVERED' WHERE id=?", e.getId());
        JsonNode res = call(post(API + "/bulk/" + job2 + "/execute"), admin(A), null, 200, key());
        assertEquals(1, res.get("successCount").asInt());
        assertEquals(1, res.get("failureCount").asInt());
        assertEquals("CANCELLATION_REQUESTED", statusOf(d.getId()));

        // release, assign, note, carrier
        long rel = call(post(API + "/bulk/preview"), admin(A), Map.of("action", "RELEASE", "orderIds", List.of(a.getId(), b.getId(), d.getId()), "params", Map.of()), 200).get("id").asLong();
        assertEquals(2, call(get(API + "/bulk/" + rel), admin(A), null, 200).get("eligibleCount").asInt());
        call(post(API + "/bulk/" + rel + "/execute"), admin(A), null, 200, key());
        assertEquals("CONFIRMED", statusOf(a.getId()));
        assertEquals("PROCESSING", statusOf(b.getId()));
        long assign = call(post(API + "/bulk/preview"), admin(A), Map.of("action", "ASSIGN_OPERATOR", "orderIds", List.of(a.getId(), b.getId()), "params", Map.of("operatorId", 77)), 200).get("id").asLong();
        call(post(API + "/bulk/" + assign + "/execute"), admin(A), null, 200, key());
        assertEquals(77L, orders.findById(a.getId()).orElseThrow().getAssignedOperatorId());
        assertEquals(1, call(get(API + "/orders?operatorId=77&status=CONFIRMED"), admin(A), null, 200).get("totalElements").asInt());
        long note = call(post(API + "/bulk/preview"), admin(A), Map.of("action", "ADD_NOTE", "orderIds", List.of(a.getId(), b.getId()), "params", Map.of("note", "Check packaging")), 200).get("id").asLong();
        call(post(API + "/bulk/" + note + "/execute"), admin(A), null, 200, key());
        assertEquals(1, call(get(API + "/orders/" + a.getId() + "/notes"), admin(A), null, 200).size());
        long carrier = call(post(API + "/bulk/preview"), admin(A), Map.of("action", "SET_CARRIER", "orderIds", List.of(a.getId()), "params", Map.of("carrier", "UPS")), 200).get("id").asLong();
        call(post(API + "/bulk/" + carrier + "/execute"), admin(A), null, 200, key());
        assertEquals("UPS", orders.findById(a.getId()).orElseThrow().getCarrier());

        // export via bulk respects customer privacy
        long exp = call(post(API + "/bulk/preview"), token(A, 8, List.of("order:view", "order:view-all-stores", "order:bulk", "order:export"), null), Map.of("action", "EXPORT", "orderIds", List.of(a.getId())), 200).get("id").asLong();
        JsonNode exported = call(post(API + "/bulk/" + exp + "/execute"), token(A, 8, List.of("order:view", "order:view-all-stores", "order:bulk", "order:export"), null), null, 200, key());
        assertTrue(exported.get("csv").asText().contains(AdminSupport.orderNumber(a.getId())));
        assertFalse(exported.get("csv").asText().contains("Alice"));
        // size limit comes from tenant settings
        call(put(API + "/settings"), admin(A), Map.of("maxBulkSize", 2), 200);
        call(post(API + "/bulk/preview"), admin(A), Map.of("action", "ADD_NOTE", "orderIds", List.of(a.getId(), b.getId(), d.getId()), "params", Map.of("note", "x")), 400);
    }

    @Test @DisplayName("executing the same bulk job concurrently applies it once")
    void bulkDuplicateExecution() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) ids.add(retail(A, "CONFIRMED").getId());
        long job = call(post(API + "/bulk/preview"), admin(A), Map.of("action", "ADD_NOTE", "orderIds", ids, "params", Map.of("note", "once only")), 200).get("id").asLong();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<JsonNode>> fs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final String k = "bulk-exec-" + i + "-" + System.nanoTime();
            fs.add(pool.submit(() -> {
                go.await();
                MvcResult r = mvc.perform(post(API + "/bulk/" + job + "/execute").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A)).header("Idempotency-Key", k)).andReturn();
                return json.readTree(r.getResponse().getContentAsString());
            }));
        }
        go.countDown();
        int firstRuns = 0;
        for (Future<JsonNode> f : fs) if (!f.get(60, TimeUnit.SECONDS).path("alreadyExecuted").asBoolean(false)) firstRuns++;
        pool.shutdown();
        assertEquals(1, firstRuns);
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM order_notes", Integer.class)); // one note per order, not five
    }

    // ---- dashboard, reports, views, returns, audit --------------------------------------------------------------------

    @Test @DisplayName("dashboard and report numbers come from real order data and honour filters")
    void dashboardAndReports() throws Exception {
        Order d1 = order(A, "main", "RETAIL", "DELIVERED", "100.00", "A"), d2 = order(A, "north", "RETAIL", "DELIVERED", "50.00", "B");
        order(A, "main", "RETAIL", "CANCELLED", "70.00", "C");
        order(A, "main", "RETAIL", "PAYMENT_FAILED", "20.00", "D");
        order(A, "main", "RETAIL", "CREATED", "10.00", "E");
        order(A, "main", "RESTAURANT", "RECEIVED", "15.00", "F");
        jdbc.update("UPDATE orders SET payment_status='PENDING' WHERE total_amount=10");
        OffsetDateTime now = OffsetDateTime.now();
        // Stage times relative to creation (independent of the clock): processing +50/+130 min, shipped +20/+60 min later, delivered +30/+10 min after that.
        jdbc.update("UPDATE orders SET processing_at=?, shipped_at=?, delivered_at=? WHERE id=?", now.plusMinutes(50), now.plusMinutes(70), now.plusMinutes(100), d1.getId());
        jdbc.update("UPDATE orders SET processing_at=?, shipped_at=?, delivered_at=? WHERE id=?", now.plusMinutes(130), now.plusMinutes(190), now.plusMinutes(200), d2.getId());
        order(B, "main", "RETAIL", "DELIVERED", "999.00", "Other tenant");

        JsonNode m = call(get(API + "/dashboard"), admin(A), null, 200);
        assertEquals(6, m.get("ordersToday").asInt());
        assertEquals(6, m.get("ordersInRange").asInt());
        assertEquals(1, m.get("newOrders").asInt());
        assertEquals(1, m.get("awaitingConfirmation").asInt());
        assertEquals(2, m.get("delivered").asInt());
        assertEquals(1, m.get("cancelled").asInt());
        assertEquals(1, m.get("failed").asInt());
        assertEquals(0, new BigDecimal("175.00").compareTo(m.get("grossOrderValue").decimalValue())); // 100+50+10+15; cancelled and payment-failed excluded
        assertEquals(0, new BigDecimal("10.00").compareTo(m.get("pendingPaymentValue").decimalValue()));
        assertEquals(90, m.get("averageProcessingMinutes").asInt()); // (50 + 130) / 2
        assertEquals(40, m.get("averageFulfillmentMinutes").asInt()); // (20 + 60) / 2
        assertEquals(20, m.get("averageDeliveryMinutes").asInt()); // (30 + 10) / 2
        assertTrue(m.get("definitions").has("queues"));
        // filters
        assertEquals(1, call(get(API + "/dashboard?storeId=north"), admin(A), null, 200).get("delivered").asInt());
        assertEquals(1, call(get(API + "/dashboard?orderType=RESTAURANT"), admin(A), null, 200).get("ordersInRange").asInt());
        assertEquals(0, call(get(API + "/dashboard?from=2020-01-01&to=2020-01-02"), admin(A), null, 200).get("ordersInRange").asInt());
        call(get(API + "/dashboard?from=2026-10-31&to=2026-10-01"), admin(A), null, 400);
        assertEquals(1, call(get(API + "/dashboard"), admin(B), null, 200).get("delivered").asInt());

        JsonNode byStatus = call(get(API + "/reports/summary?groupBy=status"), admin(A), null, 200);
        long delivered = 0;
        for (JsonNode s : byStatus.get("series")) if (s.get("key").asText().equals("DELIVERED")) delivered = s.get("orders").asLong();
        assertEquals(2, delivered);
        assertEquals(6, byStatus.get("totalOrders").asInt());
        assertEquals(2, call(get(API + "/reports/summary?groupBy=store"), admin(A), null, 200).get("series").size());
        assertFalse(call(get(API + "/reports/summary?groupBy=day"), admin(A), null, 200).get("series").isEmpty());
        call(get(API + "/reports/summary?groupBy=password"), admin(A), null, 400);
    }

    @Test @DisplayName("saved views, CSV export hardening, notes and the returns/refund window")
    void viewsExportNotesReturns() throws Exception {
        Order o = order(A, "main", "RETAIL", "DELIVERED", "40.00", "=HYPERLINK(\"http://evil\")");
        call(post(API + "/saved-views"), admin(A), Map.of("name", "Late orders", "filters", Map.of("slaBreached", true, "view", "PROCESSING", "columns", List.of("orderNumber", "status"))), 200);
        call(post(API + "/saved-views"), admin(A), Map.of("name", "Late orders", "filters", Map.of("view", "NEW")), 409);
        call(post(API + "/saved-views"), admin(A), Map.of("name", "Bad", "filters", Map.of("sql", "1=1")), 400);
        call(post(API + "/saved-views"), admin(A), Map.of("name", "<b>x</b>", "filters", Map.of()), 400);
        JsonNode views = call(get(API + "/saved-views"), admin(A), null, 200);
        assertEquals(1, views.size());
        assertEquals("PROCESSING", views.get(0).at("/filters/view").asText());
        assertEquals(0, call(get(API + "/saved-views"), token(A, 99, ALL, null), null, 200).size()); // other user, same tenant
        mvc.perform(delete(API + "/saved-views/" + views.get(0).get("id").asLong()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(A, 99, ALL, null))).andExpect(status().isNotFound());
        mvc.perform(delete(API + "/saved-views/" + views.get(0).get("id").asLong()).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isNoContent());

        String csv = mvc.perform(get(API + "/orders/export?status=DELIVERED").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("'=HYPERLINK"), "formula cells must be neutralised");
        assertFalse(csv.contains(",=HYPERLINK"));

        call(post(API + "/orders/" + o.getId() + "/notes"), admin(A), Map.of("kind", "INTERNAL", "body", "Packed with care"), 200);
        call(post(API + "/orders/" + o.getId() + "/notes"), admin(A), Map.of("kind", "COMMUNICATION", "body", "Emailed the customer"), 200);
        call(post(API + "/orders/" + o.getId() + "/notes"), admin(A), Map.of("kind", "SECRET", "body", "x"), 400);
        call(post(API + "/orders/" + o.getId() + "/notes"), admin(A), Map.of("body", " "), 400);
        assertEquals(2, call(get(API + "/orders/" + o.getId() + "/notes"), admin(A), null, 200).size());

        // returns window: lists the tenant's returns; refund state is read-only here
        jdbc.update("INSERT INTO order_return(order_id, user_id, reason, status, refund_amount, created_at) VALUES (?,?,?,?,?,?)", o.getId(), 42L, "Damaged", "RECEIVED", new BigDecimal("40.00"), OffsetDateTime.now());
        JsonNode ret = call(get(API + "/returns"), admin(A), null, 200);
        assertEquals(1, ret.get("totalElements").asInt());
        assertEquals("RECEIVED", ret.at("/content/0/status").asText());
        assertFalse(ret.at("/content/0/refundConfirmed").asBoolean());
        assertEquals(0, call(get(API + "/returns"), admin(B), null, 200).get("totalElements").asInt());
        JsonNode detail = call(get(API + "/orders/" + o.getId()), admin(A), null, 200);
        assertEquals(1, detail.get("returns").size());
        assertEquals(40, detail.at("/amounts/payable").asInt());
    }

    @Test @DisplayName("the legacy status and cancel endpoints now go through the same state machine and history")
    void legacyEndpointsAreGuarded() throws Exception {
        Order o = new Order();
        o.setUserId(5L); o.setStatus("PLACED"); o.setTotalAmount(new BigDecimal("20.00"));
        Order saved = orders.save(o);
        mvc.perform(put("/api/orders/" + saved.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/orders/" + saved.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAYMENT_CONFIRMED\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/orders/" + saved.getId() + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + saved.getId() + "/cancel")).andExpect(status().isOk());
        assertEquals("CANCELLED", statusOf(saved.getId()));
        mvc.perform(post("/api/orders/" + saved.getId() + "/cancel")).andExpect(status().isBadRequest());
        List<String> sources = jdbc.queryForList("SELECT source FROM order_status_history WHERE order_id=? ORDER BY id", String.class, saved.getId());
        assertEquals(List.of("LEGACY", "LEGACY"), sources);
        // delivered orders can no longer be cancelled through the legacy endpoint either
        Order delivered = orders.save(retail(A, "DELIVERED"));
        mvc.perform(post("/api/orders/" + delivered.getId() + "/cancel")).andExpect(status().isConflict());
    }
}

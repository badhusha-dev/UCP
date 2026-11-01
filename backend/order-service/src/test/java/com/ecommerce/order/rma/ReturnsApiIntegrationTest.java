package com.ecommerce.order.rma;

import com.ecommerce.order.admin.security.AdminAuthorizationClient;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderProducerService;
import com.ecommerce.order.service.ReturnStockClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Returns & Refund Management over HTTP: real migration SQL on H2, real JWT verification, real lifecycle, Payment and Delivery simulated through their callbacks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"SERVICE_TO_SERVICE_TOKEN=test-service-token", "orders.exceptions.scan-initial-delay-ms=3600000", "returns.attachments.dir=${java.io.tmpdir}/ucp-test-evidence"})
class ReturnsApiIntegrationTest {
    private static final UUID A = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");
    private static final List<String> RET = List.of("order:view", "order:view-all-stores", "return:view", "return:view-customer", "return:create", "return:review", "return:approve", "return:reject", "return:pickup",
            "return:receive", "return:inspect", "return:refund-process", "return:refund-approve", "return:exchange", "return:policy-manage", "return:policy-approve", "return:config", "return:exception-manage",
            "return:audit-view", "return:analytics-view", "return:export");
    private static final String API = "/api/orders/admin/rma";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private OrderRepository orders;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private AdminAuthorizationClient grants;
    @MockBean private OrderProducerService producer;
    @MockBean private CatalogClient catalog;
    @MockBean private ReturnStockClient stock;
    @Value("${jwt.secret}") private String secret;

    @BeforeEach
    void reset() {
        wipe();
        doNothing().when(grants).requireCurrentGrant(anyString());
        when(catalog.categories(any())).thenReturn(Map.of());
        when(catalog.product(anyLong())).thenAnswer(i -> Optional.of(new CatalogClient.ProductInfo(i.getArgument(0), "Product " + i.getArgument(0), new BigDecimal("10.00"), null, true)));
        when(catalog.availableStock(anyLong())).thenReturn(OptionalInt.of(100));
    }

    @AfterEach
    void cleanUp() { wipe(); }

    private void wipe() {
        for (String t : List.of("return_refund_attempts", "return_refunds", "exchange_requests", "reverse_pickup_requests", "return_exception_events", "return_exceptions", "return_inspection_items", "return_inspections",
                "return_attachments", "return_internal_notes", "return_status_history", "return_audit_logs", "return_settings", "return_policy_versions", "return_policies",
                "order_processed_events", "order_exception_events", "order_exceptions", "order_fulfillment_items", "order_fulfillments", "order_notes", "order_admin_actions", "order_status_history")) jdbc.update("DELETE FROM " + t);
        jdbc.update("DELETE FROM order_return_items");
        jdbc.update("DELETE FROM order_return");
        jdbc.update("DELETE FROM order_tracking_event");
        orders.deleteAll();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private String token(UUID tenant, long user, List<String> perms, List<String> stores) {
        var b = Jwts.builder().setSubject("u" + user + "@" + tenant).setId(UUID.randomUUID().toString()).setIssuer("universal-commerce").setAudience("commerce-api")
                .setExpiration(new Date(System.currentTimeMillis() + 600_000)).claim("id", user).claim("tenant_id", tenant.toString()).claim("tenant_role", "RETURNS_MANAGER")
                .claim("application_code", "ECOMMERCE_ADMIN").claim("permissions", perms);
        if (stores != null) b.claim("store_ids", stores);
        return b.signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
    }
    private String admin(UUID t) { return token(t, 1, RET, null); }
    private String second(UUID t) { return token(t, 2, RET, null); }
    private String without(UUID t, String... removed) { List<String> p = new ArrayList<>(RET); p.removeAll(List.of(removed)); return token(t, 7, p, null); }

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

    private Order order(UUID tenant, String store, String type, boolean delivered) {
        Order o = new Order();
        o.setTenantId(tenant); o.setStoreId(store); o.setOrderType(type); o.setStatus(delivered ? "DELIVERED" : "CONFIRMED"); o.setUserId(42L);
        o.setTotalAmount(new BigDecimal("50.00")); o.setCustomerName("Alice Smith"); o.setCustomerEmail("alice.smith@example.com"); o.setCustomerPhone("+1 555 0100");
        o.setDeliveryAddress("1 Main St"); o.setPaymentStatus("PAID"); o.setCreatedAt(LocalDateTime.now().minusDays(2));
        if (delivered) o.setDeliveredAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        OrderItem i = new OrderItem(); i.setProductId(1L); i.setProductName("Widget"); i.setPrice(new BigDecimal("10.00")); i.setQuantity(5);
        o.addItem(i);
        return orders.save(o);
    }
    private Order delivered(UUID t) { return order(t, "main", "RETAIL", true); }
    private long itemId(Order o) { return o.getItems().get(0).getId(); }

    private Map<String, Object> body(Order o, int qty, String reason) {
        return Map.of("orderId", o.getId(), "reasonCode", reason, "details", "Customer says so", "lines", List.of(Map.of("orderItemId", itemId(o), "quantity", qty)));
    }
    private long create(UUID t, Order o, int qty, String reason) throws Exception {
        return call(post(API + "/returns"), admin(t), body(o, qty, reason), 201).get("id").asLong();
    }
    private JsonNode details(UUID t, long id) throws Exception { return call(get(API + "/returns/" + id), admin(t), null, 200); }
    private String stateOf(UUID t, long id) throws Exception { return details(t, id).get("status").asText(); }

    private JsonNode action(UUID t, long id, String path, Object b, int status) throws Exception { return call(post(API + "/returns/" + id + "/" + path), admin(t), b == null ? Map.of() : b, status); }
    private JsonNode payment(String eventId, String attemptKey, String result, String ref, String amount, UUID tenant, int status) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", eventId); m.put("attemptKey", attemptKey); m.put("status", result); m.put("paymentReference", ref); m.put("amount", amount == null ? null : new BigDecimal(amount)); m.put("failureReason", result.equals("FAILED") ? "Card network declined" : null);
        MockHttpServletRequestBuilder b = post("/internal/return-events/refund").header("X-Service-Token", "test-service-token").header("X-Tenant-Id", tenant.toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(m));
        MvcResult r = mvc.perform(b).andExpect(status().is(status)).andReturn();
        String c = r.getResponse().getContentAsString();
        return c.startsWith("{") ? json.readTree(c) : null;
    }
    private JsonNode delivery(String eventId, String ref, String result, UUID tenant, int status) throws Exception {
        Map<String, Object> m = Map.of("eventId", eventId, "pickupReference", ref, "status", result, "failureReason", "Nobody home");
        MvcResult r = mvc.perform(post("/internal/return-events/pickup").header("X-Service-Token", "test-service-token").header("X-Tenant-Id", tenant.toString()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(m)))
                .andExpect(status().is(status)).andReturn();
        String c = r.getResponse().getContentAsString();
        return c.startsWith("{") ? json.readTree(c) : null;
    }

    private JsonNode schedule(UUID t, long id) throws Exception {
        return call(post(API + "/returns/" + id + "/pickup"), admin(t), Map.of("from", OffsetDateTime.now().plusHours(1).toString(), "to", OffsetDateTime.now().plusHours(3).toString()), 200);
    }

    /** approve → pickup → Delivery says it reached the warehouse → RECEIVED */
    private long received(UUID t, Order o, int qty) throws Exception {
        long id = create(t, o, qty, "NO_LONGER_NEEDED");
        action(t, id, "approve", null, 200);
        String ref = schedule(t, id).get("deliveryReference").asText();
        delivery("ev-" + key(), ref, "AT_WAREHOUSE", t, 200);
        assertEquals("RECEIVED", stateOf(t, id));
        return id;
    }

    private Map<String, Object> inspectionBody(JsonNode d, String outcome, int received, int accepted, String condition, boolean restock) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode it : d.get("items")) items.add(new HashMap<>(Map.of("returnItemId", it.get("id").asLong(), "receivedQuantity", received, "acceptedQuantity", accepted, "condition", condition, "restockable", restock)));
        return Map.of("packagingCondition", "INTACT", "accessoriesReceived", true, "outcome", outcome, "items", items);
    }

    /** Through inspection to REFUND_PENDING, then calculated. Returns the refund id. */
    private long refundReady(UUID t, long id, int qty) throws Exception {
        action(t, id, "transition", Map.of("to", "INSPECTION_PENDING"), 200);
        action(t, id, "inspection", inspectionBody(details(t, id), "ACCEPTED_FOR_REFUND", qty, qty, "NEW", true), 200);
        assertEquals("INSPECTION_COMPLETED", stateOf(t, id));
        return action(t, id, "refund", Map.of(), 200).get("id").asLong();
    }

    // ---- access control and tenant isolation ------------------------------------------------------------------------

    @Test @DisplayName("401 without a valid admin token, 403 per missing permission, callbacks need the service token")
    void authentication() throws Exception {
        Order o = delivered(A);
        long id = create(A, o, 1, "OTHER");
        mvc.perform(get(API + "/returns")).andExpect(status().isUnauthorized());
        mvc.perform(get(API + "/returns").header(HttpHeaders.AUTHORIZATION, "Bearer a.b.c")).andExpect(status().isUnauthorized());
        call(get(API + "/returns"), token(A, 9, List.of("order:view"), null), null, 403);
        call(post(API + "/returns/" + id + "/approve"), without(A, "return:approve"), Map.of(), 403);
        call(post(API + "/returns/" + id + "/reject"), without(A, "return:reject"), Map.of("reason", "x"), 403);
        call(post(API + "/returns/" + id + "/inspection"), without(A, "return:inspect"), Map.of(), 403);
        call(post(API + "/returns/" + id + "/refund"), without(A, "return:refund-process"), Map.of(), 403);
        call(post(API + "/returns/" + id + "/pickup"), without(A, "return:pickup"), Map.of(), 403);
        call(get(API + "/analytics"), without(A, "return:analytics-view"), null, 403);
        call(get(API + "/audit"), without(A, "return:audit-view"), null, 403);
        call(put(API + "/settings"), without(A, "return:config"), Map.of(), 403);
        call(post(API + "/policies"), without(A, "return:policy-manage"), Map.of("name", "x"), 403);
        mvc.perform(post("/internal/return-events/refund").contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":\"e1e1e1\",\"attemptKey\":\"rf-1-1\",\"status\":\"SUCCEEDED\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/return-events/pickup").header("X-Service-Token", "wrong").header("X-Tenant-Id", A.toString()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("another tenant sees nothing: lists, details, actions and callbacks are all 404")
    void tenantIsolation() throws Exception {
        Order o = delivered(A);
        long id = create(A, o, 1, "OTHER");
        assertEquals(0, call(get(API + "/returns"), admin(B), null, 200).get("total").asInt());
        assertEquals(1, call(get(API + "/returns"), admin(A), null, 200).get("total").asInt());
        call(get(API + "/returns/" + id), admin(B), null, 404);
        call(post(API + "/returns/" + id + "/approve"), admin(B), Map.of(), 404);
        call(post(API + "/returns/" + id + "/reject"), admin(B), Map.of("reason", "x"), 404);
        call(post(API + "/returns/" + id + "/notes"), admin(B), Map.of("body", "x"), 404);
        // B cannot start a return on A's order, nor pass A's order id through a spoofed header
        call(post(API + "/returns"), admin(B), body(o, 1, "OTHER"), 404);
        MvcResult r = mvc.perform(get(API + "/returns").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(B)).header("X-Tenant-ID", A.toString())).andExpect(status().isOk()).andReturn();
        assertEquals(0, json.readTree(r.getResponse().getContentAsString()).get("total").asInt());
        // a Payment callback for tenant B cannot touch tenant A's refund attempt
        action(A, id, "approve", null, 200);
        payment("ev-iso-1", "rf-999-1", "SUCCEEDED", "P", "10.00", B, 404);
    }

    @Test @DisplayName("store-scoped operators only see their stores; customer details are masked without return:view-customer")
    void storeScopeAndPrivacy() throws Exception {
        Order main = delivered(A);
        Order north = order(A, "north", "RETAIL", true);
        long m = create(A, main, 1, "OTHER"), n = create(A, north, 1, "OTHER");
        String northOnly = token(A, 5, RET, List.of("north"));
        List<String> noAll = new ArrayList<>(RET); noAll.remove("order:view-all-stores");
        String scoped = token(A, 5, noAll, List.of("north"));
        JsonNode list = call(get(API + "/returns"), scoped, null, 200);
        assertEquals(1, list.get("total").asInt());
        assertEquals(n, list.get("items").get(0).get("id").asLong());
        call(get(API + "/returns/" + m), scoped, null, 404);
        call(post(API + "/returns/" + m + "/approve"), scoped, Map.of(), 404);
        assertEquals(2, call(get(API + "/returns"), northOnly, null, 200).get("total").asInt(), "view-all-stores grants every store");
        List<String> noPii = new ArrayList<>(RET); noPii.remove("return:view-customer");
        JsonNode d = call(get(API + "/returns/" + m), token(A, 6, noPii, null), null, 200);
        assertEquals("A***", d.get("order").get("customer").get("name").asText());
        assertTrue(d.get("order").get("customer").get("email").asText().startsWith("a***@"));
        assertFalse(d.get("order").get("customer").has("phone"));
        assertEquals("Alice Smith", details(A, m).get("order").get("customer").get("name").asText());
    }

    // ---- request creation and the eligibility engine --------------------------------------------------------------

    @Test @DisplayName("creation: eligibility, quantity caps, undelivered orders, duplicates and idempotent retries")
    void creation() throws Exception {
        Order o = delivered(A);
        long id = create(A, o, 2, "NO_LONGER_NEEDED");
        JsonNode d = details(A, id);
        assertEquals("REQUESTED", d.get("status").asText());
        assertTrue(d.get("rmaNumber").asText().startsWith("RMA-"));
        assertEquals(0, new BigDecimal("20.00").compareTo(d.get("refundAmount").decimalValue()));
        assertEquals("Customer says so", d.get("reason").asText());
        // only 3 units remain eligible
        call(post(API + "/returns"), admin(A), body(o, 4, "OTHER"), 422);
        call(post(API + "/returns"), admin(A), body(o, 0, "OTHER"), 422);
        create(A, o, 3, "OTHER");
        call(post(API + "/returns"), admin(A), body(o, 1, "OTHER"), 422);
        // unknown reason, undelivered order, unknown item
        Order fresh = delivered(A);
        call(post(API + "/returns"), admin(A), body(fresh, 1, "BECAUSE"), 422);
        call(post(API + "/returns"), admin(A), Map.of("orderId", fresh.getId(), "reasonCode", "OTHER", "lines", List.of(Map.of("orderItemId", 99999, "quantity", 1))), 422);
        Order pending = order(A, "main", "RETAIL", false);
        call(post(API + "/returns"), admin(A), body(pending, 1, "OTHER"), 422);
        call(post(API + "/returns"), admin(A), Map.of("orderId", fresh.getId(), "reasonCode", "OTHER", "lines", List.of()), 400);
        // outside the return window
        jdbc.update("UPDATE orders SET delivered_at=? WHERE id=?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(86400L * 45)), fresh.getId());
        call(post(API + "/returns"), admin(A), body(fresh, 1, "OTHER"), 422);
        // idempotent retry returns the first result and creates one row
        Order again = delivered(A);
        String k = key();
        JsonNode first = call(post(API + "/returns"), admin(A), body(again, 1, "OTHER"), 201, k);
        JsonNode replay = call(post(API + "/returns"), admin(A), body(again, 1, "OTHER"), 200, k);
        assertEquals(first.get("id").asLong(), replay.get("id").asLong());
        assertTrue(replay.get("idempotentReplay").asBoolean());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM order_return WHERE order_id=?", Integer.class, again.getId()));
        call(post(API + "/returns"), admin(A), body(delivered(A), 1, "OTHER"), 422, k);
        verify(producer, atLeastOnce()).publishOrderEvent(argThat(e -> "RETURN_REQUESTED".equals(e.getEventType())));
    }

    @Test @DisplayName("concurrent requests for the same units: only the units that exist can be claimed")
    void concurrentCreates() throws Exception {
        Order o = delivered(A);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> jobs = new ArrayList<>();
        for (int i = 0; i < 2; i++) jobs.add(() -> mvc.perform(post(API + "/returns").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body(o, 3, "OTHER")))).andReturn().getResponse().getStatus());
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(jobs)) codes.add(f.get());
        pool.shutdown();
        Collections.sort(codes);
        assertEquals(List.of(201, 422), codes);
        assertEquals(3, jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM order_return_items", Integer.class));
    }

    @Test @DisplayName("customer endpoint creates a tenant-scoped RMA through the same engine; the old staff endpoints are retired")
    void customerPath() throws Exception {
        Order o = delivered(A);
        MvcResult r = mvc.perform(post("/api/orders/" + o.getId() + "/returns").header("X-User-Id", "42").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "Too small", "reasonCode", "SIZE_FIT", "items", List.of(Map.of("orderItemId", itemId(o), "quantity", 1)))))).andExpect(status().isOk()).andReturn();
        long id = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(A, jdbc.queryForObject("SELECT tenant_id FROM order_return WHERE id=?", UUID.class, id));
        assertEquals("CUSTOMER", jdbc.queryForObject("SELECT source FROM order_return WHERE id=?", String.class, id));
        mvc.perform(post("/api/orders/" + o.getId() + "/returns").header("X-User-Id", "43").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("reason", "Not mine", "items", List.of(Map.of("orderItemId", itemId(o), "quantity", 1)))))).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + o.getId() + "/returns").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/returns/" + id + "/process").header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REFUND\"}")).andExpect(status().isGone());
        mvc.perform(get("/api/orders/returns").header("X-User-Role", "ADMIN")).andExpect(status().isGone());
    }

    // ---- review ------------------------------------------------------------------------------------------------------

    @Test @DisplayName("approve, partial approve, reject (reason mandatory), request info, notes and assignment")
    void review() throws Exception {
        Order o = delivered(A);
        long id = create(A, o, 3, "NO_LONGER_NEEDED");
        action(A, id, "reject", Map.of(), 400);
        action(A, id, "reject", Map.of("reason", "  "), 400);
        action(A, id, "request-info", Map.of(), 400);
        action(A, id, "request-info", Map.of("note", "Please send a photo of the label"), 200);
        assertEquals("UNDER_REVIEW", stateOf(A, id));
        assertEquals("Please send a photo of the label", details(A, id).get("infoRequestedNote").asText());
        action(A, id, "notes", Map.of("body", "Customer called, second contact"), 200);
        action(A, id, "notes", Map.of("body", " "), 400);
        action(A, id, "assign", Map.of("operatorId", 77), 200);
        // approving more than requested, or nothing, is refused; fewer is a partial approval
        long item = itemId(o);
        action(A, id, "approve", Map.of("quantities", Map.of(String.valueOf(item), 4)), 400);
        action(A, id, "approve", Map.of("quantities", Map.of(String.valueOf(item), 0)), 400);
        JsonNode approved = action(A, id, "approve", Map.of("quantities", Map.of(String.valueOf(item), 2), "note", "Only 2 are within policy"), 200);
        assertEquals("APPROVED", approved.get("status").asText());
        assertEquals(0, new BigDecimal("20.00").compareTo(approved.get("refundAmount").decimalValue()));
        JsonNode d = details(A, id);
        assertEquals(2, d.get("items").get(0).get("approvedQuantity").asInt());
        assertEquals(1, d.get("notes").size());
        assertEquals("PARTIAL_APPROVE", d.get("history").get(d.get("history").size() - 1).get("action").asText());
        action(A, id, "approve", null, 409); // already decided
        action(A, id, "reject", Map.of("reason", "late"), 409);
        long rejected = create(A, delivered(A), 1, "OTHER");
        JsonNode r = action(A, rejected, "reject", Map.of("reason", "Item was used"), 200);
        assertEquals("REJECTED", r.get("status").asText());
        action(A, rejected, "transition", Map.of("to", "UNDER_REVIEW"), 409);
    }

    @Test @DisplayName("policy-required evidence blocks approval until a verified file is attached; attachments are validated, private and tenant-scoped")
    void evidenceAndAttachments() throws Exception {
        Order o = delivered(A);
        long id = create(A, o, 1, "DEFECTIVE"); // default policy: defective items need evidence
        assertTrue(details(A, id).get("evidenceRequired").asBoolean());
        action(A, id, "approve", null, 409);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", png);
        MockMultipartFile fake = new MockMultipartFile("file", "photo.png", "image/png", "<html>not an image</html>".getBytes());
        mvc.perform(multipart(API + "/returns/" + id + "/attachments").file(fake).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isUnsupportedMediaType());
        MockMultipartFile fakePng = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0});
        mvc.perform(multipart(API + "/returns/" + id + "/attachments").file(fakePng).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isBadRequest());
        action(A, id, "approve", null, 409);
        MockMultipartFile real = new MockMultipartFile("file", "../../etc/label.png", "text/plain", png.toByteArray());
        MvcResult up = mvc.perform(multipart(API + "/returns/" + id + "/attachments").file(real).param("note", "label photo").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isCreated()).andReturn();
        JsonNode att = json.readTree(up.getResponse().getContentAsString());
        assertEquals("image/png", att.get("contentType").asText());
        assertEquals("label.png", att.get("fileName").asText(), "path components are stripped from the name");
        mvc.perform(get(API + "/returns/" + id + "/attachments/" + att.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isOk());
        mvc.perform(get(API + "/returns/" + id + "/attachments/" + att.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(B))).andExpect(status().isNotFound());
        mvc.perform(get(API + "/returns/" + id + "/attachments/" + att.get("id").asLong())).andExpect(status().isUnauthorized());
        long other = create(A, delivered(A), 1, "OTHER");
        mvc.perform(get(API + "/returns/" + other + "/attachments/" + att.get("id").asLong()).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isNotFound());
        assertEquals("APPROVED", action(A, id, "approve", null, 200).get("status").asText());
    }

    // ---- lifecycle ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("manual status changes are limited to the lifecycle; money and carrier states cannot be set by hand")
    void lifecycleGuards() throws Exception {
        long id = create(A, delivered(A), 1, "OTHER");
        for (String to : List.of("REFUNDED", "REFUND_FAILED", "PICKED_UP", "IN_TRANSIT", "PICKUP_FAILED", "RETURN_LOST", "INSPECTION_COMPLETED", "REFUND_PROCESSING", "EXCHANGED", "APPROVED", "REJECTED"))
            action(A, id, "transition", Map.of("to", to), 409);
        action(A, id, "transition", Map.of("to", "NOT_A_STATUS"), 400);
        action(A, id, "transition", Map.of("to", "INSPECTION_PENDING"), 409); // not received yet
        action(A, id, "transition", Map.of("to", "CUSTOMER_CANCELLED"), 400); // reason required
        action(A, id, "transition", Map.of("to", "UNDER_REVIEW"), 200);
        JsonNode cancelled = action(A, id, "transition", Map.of("to", "CUSTOMER_CANCELLED", "reason", "Customer changed their mind"), 200);
        assertEquals("CUSTOMER_CANCELLED", cancelled.get("status").asText());
        action(A, id, "transition", Map.of("to", "UNDER_REVIEW"), 409); // terminal
        JsonNode d = details(A, id);
        JsonNode last = d.get("history").get(d.get("history").size() - 1);
        assertEquals("UNDER_REVIEW", last.get("fromStatus").asText());
        assertEquals("CUSTOMER_CANCELLED", last.get("toStatus").asText());
        assertEquals(1L, last.get("actorId").asLong());
        assertEquals("Customer changed their mind", last.get("reason").asText());
        // cancelled quantity is free again
        Order o = delivered(A);
        long a = create(A, o, 5, "OTHER");
        call(post(API + "/returns"), admin(A), body(o, 1, "OTHER"), 422);
        action(A, a, "transition", Map.of("to", "CUSTOMER_CANCELLED", "reason", "changed mind"), 200);
        create(A, o, 5, "OTHER");
    }

    @Test @DisplayName("full happy path: approve → pickup → received → inspection → refund → Payment confirms → closed, with a complete trail")
    void fullLifecycle() throws Exception {
        Order o = delivered(A);
        long id = received(A, o, 2);
        assertEquals(2, details(A, id).get("items").get(0).get("receivedQuantity").asInt());
        long refundId = refundReady(A, id, 2);
        assertEquals("REFUND_PENDING", stateOf(A, id));
        JsonNode refund = call(get(API + "/refunds/" + refundId), admin(A), null, 200);
        assertEquals("CALCULATED", refund.get("status").asText());
        assertEquals(0, new BigDecimal("20.00").compareTo(refund.get("finalAmount").decimalValue()));
        assertEquals(0, new BigDecimal("50.00").compareTo(refund.get("originalAmount").decimalValue()));
        call(post(API + "/refunds/" + refundId + "/process"), admin(A), Map.of(), 409); // not approved yet
        call(post(API + "/refunds/" + refundId + "/approve"), admin(A), Map.of(), 200);
        JsonNode processing = call(post(API + "/refunds/" + refundId + "/process"), admin(A), Map.of(), 200);
        assertEquals("PROCESSING", processing.get("status").asText());
        assertEquals("rf-" + refundId + "-1", processing.get("transactions").get(0).get("idempotencyKey").asText());
        assertEquals("REFUND_PROCESSING", stateOf(A, id));
        assertEquals("PENDING", jdbc.queryForObject("SELECT refund_status FROM orders WHERE id=?", String.class, o.getId()));
        // nothing is "refunded" until Payment says so — and an operator cannot say it for them
        action(A, id, "transition", Map.of("to", "REFUNDED"), 409);
        action(A, id, "transition", Map.of("to", "CLOSED", "reason", "skip"), 409);
        JsonNode done = payment("pay-ev-1", "rf-" + refundId + "-1", "SUCCEEDED", "PAY-777", "20.00", A, 200);
        assertEquals("COMPLETED", done.get("status").asText());
        assertEquals("REFUNDED", stateOf(A, id));
        assertEquals("PARTIAL", jdbc.queryForObject("SELECT refund_status FROM orders WHERE id=?", String.class, o.getId()));
        assertEquals(2, jdbc.queryForObject("SELECT refunded_quantity FROM order_return_items WHERE return_id=?", Integer.class, id));
        // redelivery of the same event, and a new event for an attempt that is already settled, change nothing
        JsonNode dup = payment("pay-ev-1", "rf-" + refundId + "-1", "SUCCEEDED", "PAY-777", "20.00", A, 200);
        assertTrue(dup.get("duplicate").asBoolean());
        assertNotNull(payment("pay-ev-2", "rf-" + refundId + "-1", "SUCCEEDED", "PAY-777", "20.00", A, 200).get("ignored"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM return_refunds WHERE return_id=? AND status='COMPLETED'", Integer.class, id));
        action(A, id, "transition", Map.of("to", "CLOSED", "reason", "Done"), 200);
        JsonNode d = details(A, id);
        List<String> path = new ArrayList<>();
        for (JsonNode h : d.get("history")) path.add(h.get("toStatus").asText());
        assertEquals(List.of("REQUESTED", "APPROVED", "PICKUP_SCHEDULED", "PICKED_UP", "IN_TRANSIT", "RECEIVED", "INSPECTION_PENDING", "INSPECTION_COMPLETED", "REFUND_PENDING", "REFUND_PROCESSING", "REFUNDED", "CLOSED"), path);
        assertEquals("PAYMENT", d.get("history").get(10).get("source").asText());
        assertEquals("DELIVERY", d.get("history").get(3).get("source").asText());
        verify(stock).restock(eq(1L), eq(2), anyString(), anyString());
        ArgumentCaptor<com.ecommerce.order.dto.OrderEvent> ev = ArgumentCaptor.forClass(com.ecommerce.order.dto.OrderEvent.class);
        verify(producer, atLeast(8)).publishOrderEvent(ev.capture());
        Set<String> types = new HashSet<>();
        ev.getAllValues().forEach(e -> types.add(e.getEventType()));
        assertTrue(types.containsAll(Set.of("RETURN_REQUESTED", "RETURN_APPROVED", "RETURN_RECEIVED", "RETURN_REFUND_REQUESTED", "RETURN_REFUNDED", "RETURN_REFUND_COMPLETED", "RETURN_CLOSED")), types.toString());
        // audit history is queryable and attributable
        JsonNode audit = call(get(API + "/audit?returnId=" + id), admin(A), null, 200);
        assertTrue(audit.get("total").asInt() >= 10);
        assertEquals(0, call(get(API + "/audit?returnId=" + id), admin(B), null, 200).get("total").asInt());
    }

    @Test @DisplayName("Delivery callbacks: out-of-order and repeated reports are harmless; failed pickups can be rescheduled; lost parcels are flagged")
    void pickupFlow() throws Exception {
        Order o = delivered(A);
        long id = create(A, o, 1, "OTHER");
        call(post(API + "/returns/" + id + "/pickup"), admin(A), Map.of("from", OffsetDateTime.now().plusHours(1).toString(), "to", OffsetDateTime.now().plusHours(2).toString()), 409); // not approved
        action(A, id, "approve", null, 200);
        call(post(API + "/returns/" + id + "/pickup"), admin(A), Map.of("from", OffsetDateTime.now().plusHours(3).toString(), "to", OffsetDateTime.now().plusHours(2).toString()), 400);
        JsonNode p = schedule(A, id);
        String ref = p.get("deliveryReference").asText();
        assertEquals("PICKUP_SCHEDULED", stateOf(A, id));
        call(post(API + "/returns/" + id + "/pickup"), admin(A), Map.of("from", OffsetDateTime.now().plusHours(1).toString(), "to", OffsetDateTime.now().plusHours(2).toString()), 409); // already scheduled
        delivery("d-fail-1", ref, "FAILED", A, 200);
        assertEquals("PICKUP_FAILED", stateOf(A, id));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM reverse_pickup_requests WHERE return_id=?", Integer.class, id));
        schedule(A, id);
        assertEquals("PICKUP_SCHEDULED", stateOf(A, id));
        delivery("d-pick-1", ref, "IN_TRANSIT", A, 200); // skips PICKED_UP: both steps are recorded
        assertEquals("IN_TRANSIT", stateOf(A, id));
        assertEquals(List.of("PICKUP_FAILED", "PICKED_UP", "IN_TRANSIT"), jdbc.queryForList("SELECT to_status FROM return_status_history WHERE return_id=? AND source='DELIVERY' ORDER BY id", String.class, id));
        assertNotNull(delivery("d-pick-2", ref, "PICKED_UP", A, 200).get("ignored")); // late, older report
        assertEquals("IN_TRANSIT", stateOf(A, id));
        assertTrue(delivery("d-pick-1", ref, "IN_TRANSIT", A, 200).get("duplicate").asBoolean());
        delivery("d-lost-1", ref, "LOST", A, 200);
        assertEquals("RETURN_LOST", stateOf(A, id));
        assertEquals(1, call(get(API + "/exceptions?category=LOST_SHIPMENT"), admin(A), null, 200).get("total").asInt());
        delivery("d-found-1", ref, "AT_WAREHOUSE", A, 200);
        assertEquals("RECEIVED", stateOf(A, id));
        delivery("d-bad-1", "RP-424242", "PICKED_UP", A, 404);
        delivery("d-bad-2", ref, "TELEPORTED", A, 400);
        delivery("d-other-1", ref, "PICKED_UP", B, 404);
    }

    @Test @DisplayName("restaurant claims have no pickup or inspection and refund straight from approval")
    void restaurantClaim() throws Exception {
        Order o = order(A, "kitchen", "RESTAURANT", true);
        MvcResult r = mvc.perform(post(API + "/returns").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("orderId", o.getId(), "reasonCode", "MISSING_ITEMS", "details", "No fries")))).andReturn();
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        long id = json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
        assertEquals("RESTAURANT_CLAIM", details(A, id).get("returnKind").asText());
        assertEquals(1, details(A, id).get("items").size(), "an order-level claim covers the unclaimed lines");
        call(post(API + "/returns"), admin(A), Map.of("orderId", order(A, "kitchen", "RESTAURANT", true).getId(), "reasonCode", "OTHER"), 422); // unknown claim reason
        call(post(API + "/returns"), admin(A), Map.of("orderId", o.getId(), "reasonCode", "MISSING_ITEMS"), 400); // everything is already claimed
        action(A, id, "approve", null, 409); // MISSING_ITEMS needs evidence
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", png);
        mvc.perform(multipart(API + "/returns/" + id + "/attachments").file(new MockMultipartFile("file", "bag.png", "image/png", png.toByteArray())).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isCreated());
        action(A, id, "approve", null, 200);
        call(post(API + "/returns/" + id + "/pickup"), admin(A), Map.of("from", OffsetDateTime.now().plusHours(1).toString(), "to", OffsetDateTime.now().plusHours(2).toString()), 409);
        action(A, id, "transition", Map.of("to", "INSPECTION_PENDING"), 400); // not statuses a claim has
        action(A, id, "transition", Map.of("to", "RECEIVED"), 400);
        long refundId = action(A, id, "refund", Map.of(), 200).get("id").asLong();
        assertEquals("CLAIM", call(get(API + "/refunds/" + refundId), admin(A), null, 200).get("kind").asText());
        call(post(API + "/refunds/" + refundId + "/approve"), admin(A), Map.of(), 200);
        call(post(API + "/refunds/" + refundId + "/process"), admin(A), Map.of(), 200);
        payment("claim-ev-1", "rf-" + refundId + "-1", "SUCCEEDED", "PAY-9", "50.00", A, 200);
        assertEquals("REFUNDED", stateOf(A, id));
    }

    // ---- inspection --------------------------------------------------------------------------------------------------

    @Test @DisplayName("inspection: validated quantities and outcomes, restock only for resellable accepted goods, mismatches raise review items")
    void inspection() throws Exception {
        Order o = delivered(A);
        long id = received(A, o, 3);
        action(A, id, "inspection", inspectionBody(details(A, id), "ACCEPTED_FOR_REFUND", 3, 3, "NEW", true), 409); // not queued yet
        action(A, id, "transition", Map.of("to", "INSPECTION_PENDING"), 200);
        JsonNode d = details(A, id);
        action(A, id, "inspection", inspectionBody(d, "ACCEPTED_FOR_REFUND", 4, 4, "NEW", false), 400); // more than approved
        action(A, id, "inspection", inspectionBody(d, "ACCEPTED_FOR_REFUND", 2, 3, "NEW", false), 400); // accepted > received
        action(A, id, "inspection", inspectionBody(d, "ACCEPTED_FOR_REFUND", 3, 3, "DAMAGED", true), 400); // damaged goods cannot be restocked
        action(A, id, "inspection", inspectionBody(d, "REJECTED", 3, 1, "NEW", false), 400);
        action(A, id, "inspection", inspectionBody(d, "PARTIALLY_ACCEPTED", 3, 3, "NEW", false), 400);
        action(A, id, "inspection", inspectionBody(d, "ACCEPTED_FOR_EXCHANGE", 3, 3, "NEW", false), 400); // refund, not exchange
        action(A, id, "inspection", Map.of("packagingCondition", "INTACT", "outcome", "ACCEPTED_FOR_REFUND", "items", List.of()), 400);
        action(A, id, "inspection", Map.of("packagingCondition", "BROKEN", "outcome", "ACCEPTED_FOR_REFUND", "items", List.of()), 400);
        verifyNoInteractions(stock);
        // partially accepted: 2 of 3 units, one of them not restockable
        JsonNode r = action(A, id, "inspection", inspectionBody(d, "PARTIALLY_ACCEPTED", 3, 2, "USED", false), 200);
        assertEquals("INSPECTION_COMPLETED", r.get("status").asText());
        verifyNoInteractions(stock);
        assertEquals(1, call(get(API + "/exceptions?category=INSPECTION_MISMATCH"), admin(A), null, 200).get("total").asInt());
        long refundId = action(A, id, "refund", Map.of(), 200).get("id").asLong();
        assertEquals(0, new BigDecimal("20.00").compareTo(call(get(API + "/refunds/" + refundId), admin(A), null, 200).get("finalAmount").decimalValue()), "only the 2 accepted units are refunded");
        // a rejected inspection ends the refund path
        long bad = received(A, delivered(A), 1);
        action(A, bad, "transition", Map.of("to", "INSPECTION_PENDING"), 200);
        assertEquals("INSPECTION_FAILED", action(A, bad, "inspection", inspectionBody(details(A, bad), "REJECTED", 1, 0, "DAMAGED", false), 200).get("status").asText());
        action(A, bad, "refund", Map.of(), 409);
        // manual review leaves the return waiting
        long review = received(A, delivered(A), 1);
        action(A, review, "transition", Map.of("to", "INSPECTION_PENDING"), 200);
        assertEquals("INSPECTION_PENDING", action(A, review, "inspection", inspectionBody(details(A, review), "REQUIRES_MANUAL_REVIEW", 1, 1, "USED", false), 200).get("status").asText());
        assertEquals(1, call(get(API + "/exceptions?category=MANUAL_REVIEW"), admin(A), null, 200).get("total").asInt());
    }

    // ---- refunds -----------------------------------------------------------------------------------------------------

    @Test @DisplayName("refund calculation includes proportional discount, tax and delivery fee for seller-fault full returns, and never exceeds what was paid")
    void refundCalculation() throws Exception {
        Order o = delivered(A);
        o.setDiscountAmount(new BigDecimal("5.00")); o.setTaxAmount(new BigDecimal("4.50")); o.setDeliveryCharge(new BigDecimal("5.00")); o.setTotalAmount(new BigDecimal("54.50"));
        orders.save(o);
        long id = create(A, o, 5, "DEFECTIVE"); // seller fault + every unit
        MockMultipartFile f;
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", png);
        mvc.perform(multipart(API + "/returns/" + id + "/attachments").file(new MockMultipartFile("file", "a.png", "image/png", png.toByteArray())).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isCreated());
        action(A, id, "approve", null, 200);
        action(A, id, "transition", Map.of("to", "RECEIVED"), 200);
        long refundId = refundReady(A, id, 5);
        JsonNode refund = call(get(API + "/refunds/" + refundId), admin(A), null, 200);
        assertEquals(0, new BigDecimal("54.50").compareTo(refund.get("finalAmount").decimalValue()), refund.toString());
        assertEquals(0, new BigDecimal("5.00").compareTo(refund.get("deliveryFeeRefund").decimalValue()));
        assertEquals(0, new BigDecimal("50.00").compareTo(refund.get("itemsAmount").decimalValue()));
        assertEquals(0, new BigDecimal("5.00").compareTo(refund.get("discountAllocation").decimalValue()));
    }

    @Test @DisplayName("refunds above the approval threshold need a second person; the amount can't be edited")
    void makerChecker() throws Exception {
        call(put(API + "/settings"), admin(A), Map.of("refundApprovalThreshold", 15, "highValueRefundThreshold", 500), 200);
        long id = received(A, delivered(A), 2);
        long refundId = refundReady(A, id, 2);
        call(post(API + "/refunds/" + refundId + "/approve"), admin(A), Map.of(), 403); // 20.00 > 15.00 and the same person calculated it
        call(post(API + "/refunds/" + refundId + "/approve"), without(A, "return:refund-approve"), Map.of(), 403);
        call(post(API + "/refunds/" + refundId + "/approve"), second(A), Map.of(), 200);
        call(post(API + "/refunds/" + refundId + "/approve"), second(A), Map.of(), 409);
        mvc.perform(post(API + "/refunds/" + refundId + "/approve").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(B))).andExpect(status().isNotFound());
    }

    @Test @DisplayName("two operators pressing Process at once create one Payment attempt, not two")
    void concurrentProcess() throws Exception {
        long id = received(A, delivered(A), 2);
        long refundId = refundReady(A, id, 2);
        call(post(API + "/refunds/" + refundId + "/approve"), admin(A), Map.of(), 200);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> jobs = new ArrayList<>();
        for (int i = 0; i < 2; i++) jobs.add(() -> mvc.perform(post(API + "/refunds/" + refundId + "/process").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andReturn().getResponse().getStatus());
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(jobs)) codes.add(f.get());
        pool.shutdown();
        Collections.sort(codes);
        assertEquals(List.of(200, 409), codes);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM return_refund_attempts WHERE refund_request_id=?", Integer.class, refundId));
    }

    @Test @DisplayName("a refund cannot be prepared twice, and combined refunds can't exceed the order total")
    void refundCap() throws Exception {
        Order o = delivered(A);
        long first = received(A, o, 3);
        long refundId = refundReady(A, first, 3);
        action(A, first, "refund", Map.of(), 409);
        call(post(API + "/refunds/" + refundId + "/approve"), admin(A), Map.of(), 200);
        long second = received(A, o, 2);
        long refund2 = refundReady(A, second, 2);
        // force the second request above what remains (50 - 30 reserved = 20 is fine); push it over and approval is refused
        jdbc.update("UPDATE return_refunds SET final_amount=? WHERE id=?", new BigDecimal("25.00"), refund2);
        call(post(API + "/refunds/" + refund2 + "/approve"), admin(A), Map.of(), 409);
        jdbc.update("UPDATE return_refunds SET final_amount=? WHERE id=?", new BigDecimal("20.00"), refund2);
        call(post(API + "/refunds/" + refund2 + "/approve"), admin(A), Map.of(), 200);
    }

    @Test @DisplayName("failure and retry: a declined refund can be retried with a new attempt key; a wrong amount is refused and flagged")
    void failureAndRetry() throws Exception {
        Order o = delivered(A);
        long id = received(A, o, 2);
        long refundId = refundReady(A, id, 2);
        call(post(API + "/refunds/" + refundId + "/approve"), admin(A), Map.of(), 200);
        call(post(API + "/refunds/" + refundId + "/process"), admin(A), Map.of(), 200);
        call(post(API + "/refunds/" + refundId + "/process"), admin(A), Map.of(), 409); // attempt in flight
        // confirmation with the wrong amount does not complete anything
        payment("pay-bad-amt", "rf-" + refundId + "-1", "SUCCEEDED", "PAY-1", "19.00", A, 409);
        assertEquals("REFUND_PROCESSING", stateOf(A, id));
        assertEquals(1, call(get(API + "/exceptions?category=REFUND_AMOUNT_MISMATCH"), admin(A), null, 200).get("total").asInt());
        assertEquals("PROCESSING", call(get(API + "/refunds/" + refundId), admin(A), null, 200).get("status").asText());
        payment("pay-fail-1", "rf-" + refundId + "-1", "FAILED", null, null, A, 200);
        assertEquals("REFUND_FAILED", stateOf(A, id));
        assertEquals("FAILED", jdbc.queryForObject("SELECT refund_status FROM orders WHERE id=?", String.class, o.getId()));
        assertEquals(1, call(get(API + "/exceptions?category=REFUND_FAILURE"), admin(A), null, 200).get("total").asInt());
        JsonNode retry = call(post(API + "/refunds/" + refundId + "/process"), admin(A), Map.of(), 200);
        assertEquals(2, retry.get("attemptCount").asInt());
        assertEquals("REFUND_PROCESSING", stateOf(A, id));
        // a late success for the dead first attempt is ignored; the second attempt completes it
        assertNotNull(payment("pay-late", "rf-" + refundId + "-1", "SUCCEEDED", "OLD", "20.00", A, 200).get("ignored"));
        assertEquals("REFUND_PROCESSING", stateOf(A, id));
        payment("pay-ok-2", "rf-" + refundId + "-2", "SUCCEEDED", "PAY-2", "20.00", A, 200);
        assertEquals("REFUNDED", stateOf(A, id));
        assertEquals("PAY-2", jdbc.queryForObject("SELECT refund_reference FROM orders WHERE id=?", String.class, o.getId()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM return_refund_attempts WHERE refund_request_id=?", Integer.class, refundId));
    }

    // ---- exchanges ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("exchange: stock-checked, approved, accepted by inspection, then a replacement order that waits for any extra payment")
    void exchange() throws Exception {
        Order o = delivered(A);
        long id = call(post(API + "/returns"), admin(A), Map.of("orderId", o.getId(), "reasonCode", "SIZE_FIT", "resolution", "EXCHANGE", "lines", List.of(Map.of("orderItemId", itemId(o), "quantity", 1))), 201).get("id").asLong();
        long returnItem = details(A, id).get("items").get(0).get("id").asLong();
        action(A, id, "exchanges", Map.of("returnItemId", returnItem, "newProductId", 9, "quantity", 1), 409); // not approved yet
        action(A, id, "approve", null, 200);
        when(catalog.product(9L)).thenReturn(Optional.of(new CatalogClient.ProductInfo(9, "Bigger Widget", new BigDecimal("14.00"), null, true)));
        when(catalog.availableStock(9L)).thenReturn(OptionalInt.of(0));
        action(A, id, "exchanges", Map.of("returnItemId", returnItem, "newProductId", 9, "quantity", 1), 409); // out of stock
        when(catalog.availableStock(9L)).thenReturn(OptionalInt.empty());
        action(A, id, "exchanges", Map.of("returnItemId", returnItem, "newProductId", 9, "quantity", 1), 409); // unknown stock is not "in stock"
        when(catalog.availableStock(9L)).thenReturn(OptionalInt.of(10));
        action(A, id, "exchanges", Map.of("returnItemId", returnItem, "newProductId", 9, "quantity", 2), 400); // more than returned
        JsonNode ex = action(A, id, "exchanges", Map.of("returnItemId", returnItem, "newProductId", 9, "quantity", 1), 201);
        assertEquals(0, new BigDecimal("4.00").compareTo(ex.get("priceDifference").decimalValue()));
        long exId = ex.get("id").asLong();
        call(post(API + "/exchanges/" + exId + "/fulfill"), admin(A), Map.of(), 409); // not approved
        call(post(API + "/exchanges/" + exId + "/decision"), admin(A), Map.of("approve", true), 200);
        call(post(API + "/exchanges/" + exId + "/fulfill"), admin(A), Map.of(), 409); // goods not back yet
        String ref = schedule(A, id).get("deliveryReference").asText();
        delivery("ex-evt-1", ref, "AT_WAREHOUSE", A, 200);
        action(A, id, "transition", Map.of("to", "INSPECTION_PENDING"), 200);
        action(A, id, "inspection", inspectionBody(details(A, id), "ACCEPTED_FOR_REFUND", 1, 1, "NEW", false), 400); // it's an exchange
        action(A, id, "inspection", inspectionBody(details(A, id), "ACCEPTED_FOR_EXCHANGE", 1, 1, "NEW", true), 200);
        action(A, id, "refund", Map.of(), 409); // exchanges do not refund the item
        // stock disappears between approval and fulfillment
        when(catalog.availableStock(9L)).thenReturn(OptionalInt.of(0));
        call(post(API + "/exchanges/" + exId + "/fulfill"), admin(A), Map.of(), 409);
        when(catalog.availableStock(9L)).thenReturn(OptionalInt.of(10));
        JsonNode done = call(post(API + "/exchanges/" + exId + "/fulfill"), admin(A), Map.of(), 200, key());
        assertEquals("FULFILLMENT_CREATED", done.get("status").asText());
        assertEquals("EXCHANGE_PENDING", stateOf(A, id));
        long newOrder = done.get("replacementOrderId").asLong();
        Order replacement = orders.findById(newOrder).orElseThrow();
        assertEquals(id, replacement.getExchangeOfReturnId());
        assertEquals(A, replacement.getTenantId());
        assertEquals("CREATED", replacement.getStatus());
        assertEquals("PENDING", replacement.getPaymentStatus(), "the price difference must be paid first");
        assertTrue(replacement.isExchangePaymentRequired());
        // the replacement cannot be processed or shipped until Payment confirms the difference
        call(post("/api/orders/admin/orders/" + newOrder + "/transition"), token(A, 1, List.of("order:view", "order:view-all-stores", "order:process", "order:confirm"), null), Map.of("to", "PROCESSING"), 409, key());
        mvc.perform(post("/internal/order-events/payment").header("X-Service-Token", "test-service-token").header("X-Tenant-Id", A.toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("eventId", "pay-diff-1", "orderId", newOrder, "status", "PAID", "method", "CARD", "reference", "PAY-D")))).andExpect(status().isOk());
        assertEquals("PAID", orders.findById(newOrder).orElseThrow().getPaymentStatus());
        // a cheaper replacement creates a price-difference refund instead
        long id2 = call(post(API + "/returns"), admin(A), Map.of("orderId", delivered(A).getId(), "reasonCode", "SIZE_FIT", "resolution", "EXCHANGE", "lines", List.of(Map.of("orderItemId", itemId(orders.findAll().stream().filter(x -> x.getId() > newOrder).findFirst().orElseThrow()), "quantity", 1))), 201).get("id").asLong();
        when(catalog.product(8L)).thenReturn(Optional.of(new CatalogClient.ProductInfo(8, "Small Widget", new BigDecimal("7.00"), null, true)));
        when(catalog.availableStock(8L)).thenReturn(OptionalInt.of(10));
        action(A, id2, "approve", null, 200);
        long item2 = details(A, id2).get("items").get(0).get("id").asLong();
        long ex2 = action(A, id2, "exchanges", Map.of("returnItemId", item2, "newProductId", 8, "quantity", 1), 201).get("id").asLong();
        call(post(API + "/exchanges/" + ex2 + "/decision"), admin(A), Map.of("approve", true), 200);
        String ref2 = schedule(A, id2).get("deliveryReference").asText();
        delivery("ex-evt-2", ref2, "AT_WAREHOUSE", A, 200);
        action(A, id2, "transition", Map.of("to", "INSPECTION_PENDING"), 200);
        action(A, id2, "inspection", inspectionBody(details(A, id2), "ACCEPTED_FOR_EXCHANGE", 1, 1, "NEW", false), 200);
        JsonNode cheaper = call(post(API + "/exchanges/" + ex2 + "/fulfill"), admin(A), Map.of(), 200);
        Order repl2 = orders.findById(cheaper.get("replacementOrderId").asLong()).orElseThrow();
        assertFalse(repl2.isExchangePaymentRequired());
        assertEquals("PAID", repl2.getPaymentStatus());
        assertEquals("CONFIRMED", repl2.getStatus());
        JsonNode diffRefund = details(A, id2).get("refunds").get(0);
        assertEquals("PRICE_DIFFERENCE", diffRefund.get("kind").asText());
        assertEquals(0, new BigDecimal("3.00").compareTo(diffRefund.get("finalAmount").decimalValue()));
    }

    @Test @DisplayName("exchange proposals are refused when the policy forbids them")
    void exchangePolicy() throws Exception {
        JsonNode policy = call(get(API + "/policies/defaults"), admin(A), null, 200);
        com.fasterxml.jackson.databind.node.ObjectNode cfg = (com.fasterxml.jackson.databind.node.ObjectNode) policy.get("config").deepCopy();
        cfg.put("exchangeAllowed", false);
        JsonNode created = call(post(API + "/policies"), admin(A), Map.of("name", "No exchanges", "config", cfg), 201);
        long pid = created.get("id").asLong(), vid = created.get("versions").get(0).get("id").asLong();
        call(post(API + "/policies/" + pid + "/versions/" + vid + "/submit"), admin(A), Map.of(), 200);
        call(post(API + "/policies/" + pid + "/versions/" + vid + "/decision"), second(A), Map.of("approve", true), 200);
        call(post(API + "/policies/" + pid + "/versions/" + vid + "/publish"), second(A), Map.of(), 200);
        Order o = delivered(A);
        call(post(API + "/returns"), admin(A), Map.of("orderId", o.getId(), "reasonCode", "SIZE_FIT", "resolution", "EXCHANGE", "lines", List.of(Map.of("orderItemId", itemId(o), "quantity", 1))), 400);
    }

    // ---- policies ----------------------------------------------------------------------------------------------------

    @Test @DisplayName("policy lifecycle: draft → review (no self-review) → publish → rollback; decided returns keep the version they were decided under")
    void policyLifecycle() throws Exception {
        Order o = delivered(A);
        long before = create(A, o, 1, "NO_LONGER_NEEDED");
        assertTrue(details(A, before).get("policyVersionId").isNull());
        com.fasterxml.jackson.databind.node.ObjectNode cfg = (com.fasterxml.jackson.databind.node.ObjectNode) call(get(API + "/policies/defaults"), admin(A), null, 200).get("config").deepCopy();
        cfg.put("returnWindowDays", 7);
        call(post(API + "/policies"), admin(A), Map.of("name", "<script>", "config", cfg), 400);
        com.fasterxml.jackson.databind.node.ObjectNode bad = cfg.deepCopy();
        bad.put("returnWindowDays", 100000);
        call(post(API + "/policies"), admin(A), Map.of("name", "Bad", "config", bad), 400);
        JsonNode created = call(post(API + "/policies"), admin(A), Map.of("name", "Strict 7 day", "config", cfg), 201);
        long pid = created.get("id").asLong(), v1 = created.get("versions").get(0).get("id").asLong();
        call(post(API + "/policies"), admin(A), Map.of("name", "Dup", "config", cfg), 409);
        // a draft has no effect on new requests
        Order old = delivered(A);
        jdbc.update("UPDATE orders SET delivered_at=? WHERE id=?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(86400L * 10)), old.getId());
        create(A, old, 1, "OTHER");
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/publish"), admin(A), Map.of(), 409); // not approved
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/submit"), admin(A), Map.of(), 200);
        call(put(API + "/policies/" + pid + "/draft"), admin(A), Map.of("config", cfg), 409); // locked while in review
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/decision"), admin(A), Map.of("approve", true), 403); // own submission
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/decision"), second(A), Map.of("approve", false), 400); // reason needed
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/decision"), second(A), Map.of("approve", false, "note", "Needs a longer window"), 200);
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/publish"), admin(A), Map.of(), 409);
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/submit"), admin(A), Map.of(), 200);
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/decision"), second(A), Map.of("approve", true), 200);
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/publish"), without(A, "return:policy-approve"), Map.of(), 403);
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/publish"), second(A), Map.of(), 200);
        // published: the 10-day-old order is now outside the 7-day window
        Order late = delivered(A);
        jdbc.update("UPDATE orders SET delivered_at=? WHERE id=?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(86400L * 10)), late.getId());
        call(post(API + "/returns"), admin(A), body(late, 1, "OTHER"), 422);
        long after = create(A, delivered(A), 1, "OTHER");
        assertEquals(v1, details(A, after).get("policyVersionId").asLong());
        // the earlier case still carries the version it was filed under (the platform default), and approving it is unaffected
        assertTrue(details(A, before).get("policyVersionId").isNull());
        action(A, before, "approve", null, 200);
        // a new draft, publish, then roll back to v1
        com.fasterxml.jackson.databind.node.ObjectNode loose = cfg.deepCopy();
        loose.put("returnWindowDays", 60);
        JsonNode drafted = call(put(API + "/policies/" + pid + "/draft"), admin(A), Map.of("config", loose, "note", "Loosen"), 200);
        long v2 = drafted.get("versions").get(0).get("id").asLong();
        assertNotEquals(v1, v2);
        call(post(API + "/policies/" + pid + "/versions/" + v2 + "/submit"), admin(A), Map.of(), 200);
        call(post(API + "/policies/" + pid + "/versions/" + v2 + "/decision"), second(A), Map.of("approve", true), 200);
        call(post(API + "/policies/" + pid + "/versions/" + v2 + "/publish"), second(A), Map.of(), 200);
        assertEquals(v1, call(get(API + "/policies/" + pid + "/versions"), admin(A), null, 200).get(1).get("id").asLong());
        assertEquals("ARCHIVED", call(get(API + "/policies/" + pid + "/versions"), admin(A), null, 200).get(1).get("status").asText());
        call(post(API + "/policies/" + pid + "/versions/" + v1 + "/rollback"), second(A), Map.of("note", "Too lenient"), 200);
        JsonNode versions = call(get(API + "/policies/" + pid + "/versions"), admin(A), null, 200);
        assertEquals(3, versions.size());
        assertEquals("PUBLISHED", versions.get(0).get("status").asText());
        assertEquals(7, versions.get(0).get("config").get("returnWindowDays").asInt());
        call(get(API + "/policies"), admin(B), null, 200); // another tenant lists its own (none)
        assertEquals(0, call(get(API + "/policies"), admin(B), null, 200).size());
        call(get(API + "/policies/" + pid), admin(B), null, 404);
    }

    // ---- exceptions, settings, reports --------------------------------------------------------------------------------

    @Test @DisplayName("risk flags are signals for reviewers: duplicates and high value are flagged, scans don't pile up duplicates, nothing is blocked")
    void exceptions() throws Exception {
        call(put(API + "/settings"), admin(A), Map.of("highValueRefundThreshold", 15), 200);
        Order o = delivered(A);
        long first = create(A, o, 1, "OTHER");
        long second = create(A, o, 2, "OTHER");   // 20.00 >= 15.00 → high value; also the same item is already in an open return
        JsonNode d = details(A, second);
        assertTrue(d.get("riskFlags").toString().contains("HIGH_VALUE"), d.get("riskFlags").toString());
        assertTrue(d.get("riskFlags").toString().contains("DUPLICATE_ATTEMPT"), d.get("riskFlags").toString());
        assertEquals("REQUESTED", d.get("status").asText(), "a flag never blocks or rejects");
        for (int i = 0; i < 3; i++) call(post(API + "/returns/" + second + "/scan"), admin(A), Map.of(), 200);
        JsonNode list = call(get(API + "/exceptions?scope=open"), admin(A), null, 200);
        assertEquals(2, list.get("total").asInt());
        assertEquals(0, call(get(API + "/exceptions"), admin(B), null, 200).get("total").asInt());
        long exId = list.get("items").get(0).get("id").asLong();
        call(post(API + "/exceptions/" + exId + "/action"), admin(A), Map.of("action", "RESOLVE"), 400);
        call(post(API + "/exceptions/" + exId + "/action"), admin(A), Map.of("action", "ASSIGN", "assigneeId", 5), 200);
        call(post(API + "/exceptions/" + exId + "/action"), admin(A), Map.of("action", "ESCALATE"), 200);
        call(post(API + "/exceptions/" + exId + "/action"), admin(A), Map.of("action", "RESOLVE", "note", "Checked with the customer; genuine"), 200);
        call(post(API + "/exceptions/" + exId + "/action"), admin(A), Map.of("action", "DISMISS", "note", "x"), 409);
        call(post(API + "/exceptions/" + exId + "/action"), admin(B), Map.of("action", "DISMISS", "note", "x"), 404);
        call(post(API + "/exceptions/" + exId + "/action"), without(A, "return:exception-manage"), Map.of("action", "DISMISS", "note", "x"), 403);
        assertEquals(1, call(get(API + "/exceptions?scope=open"), admin(A), null, 200).get("total").asInt());
        action(A, first, "approve", null, 200); // still fully workable
    }

    @Test @DisplayName("refund configuration is validated, tenant-scoped and audited")
    void settings() throws Exception {
        JsonNode s = call(get(API + "/settings"), admin(A), null, 200);
        assertEquals(0, new BigDecimal("100.00").compareTo(s.get("refundApprovalThreshold").decimalValue()));
        call(put(API + "/settings"), admin(A), Map.of("refundApprovalThreshold", -1), 400);
        call(put(API + "/settings"), admin(A), Map.of("highValueRefundThreshold", 0), 400);
        call(put(API + "/settings"), admin(A), Map.of("repeatReturnCount", 1), 400);
        call(put(API + "/settings"), admin(A), Map.of("pickupFailureLimit", "many"), 400);
        call(put(API + "/settings"), admin(A), Map.of("refundSlaHours", 100000), 400);
        JsonNode u = call(put(API + "/settings"), admin(A), Map.of("refundApprovalThreshold", 250.5, "repeatReturnCount", 4), 200);
        assertEquals(0, new BigDecimal("250.50").compareTo(u.get("refundApprovalThreshold").decimalValue()));
        assertEquals(4, u.get("repeatReturnCount").asInt());
        assertEquals(0, new BigDecimal("100.00").compareTo(call(get(API + "/settings"), admin(B), null, 200).get("refundApprovalThreshold").decimalValue()));
        assertTrue(call(get(API + "/audit?entityType=SETTINGS"), admin(A), null, 200).get("total").asInt() >= 1);
    }

    @Test @DisplayName("dashboard and analytics are computed from real data and scoped to the tenant")
    void dashboard() throws Exception {
        Order o1 = delivered(A), o2 = delivered(A), o3 = delivered(A);
        long a = create(A, o1, 2, "NO_LONGER_NEEDED");
        long b = create(A, o2, 1, "SIZE_FIT");
        long c = create(A, o3, 1, "SIZE_FIT");
        action(A, b, "approve", null, 200);
        action(A, c, "reject", Map.of("reason", "worn"), 200);
        create(B, delivered(B), 1, "OTHER");
        JsonNode d = call(get(API + "/dashboard"), admin(A), null, 200);
        assertEquals(3, d.get("totalReturns").asInt());
        assertEquals(1, d.get("pendingApprovals").asInt());
        assertEquals(1, d.get("approved").asInt());
        assertEquals(1, d.get("rejected").asInt());
        assertEquals(1, d.get("awaitingPickup").asInt());
        assertEquals(3, d.get("deliveredOrders").asInt());
        assertEquals(0, new BigDecimal("100.00").compareTo(d.get("returnRatePercent").decimalValue()));
        assertEquals("SIZE_FIT", d.get("topReasons").get(0).get("reasonCode").asText());
        assertEquals(2, d.get("topReasons").get(0).get("returns").asInt());
        assertEquals(3, d.get("highReturnProducts").get(0).get("returnedQuantity").asInt());
        assertEquals(1, call(get(API + "/dashboard?status=APPROVED"), admin(A), null, 200).get("totalReturns").asInt());
        assertEquals(0, call(get(API + "/dashboard?storeId=other"), admin(A), null, 200).get("totalReturns").asInt());
        assertEquals(1, call(get(API + "/dashboard?from=2000-01-01&to=2100-01-01&storeId=main"), admin(B), null, 200).get("totalReturns").asInt());
        call(get(API + "/dashboard?from=yesterday"), admin(A), null, 400);
        JsonNode an = call(get(API + "/analytics"), admin(A), null, 200);
        assertEquals(3, an.get("byStatus").size());
        assertTrue(an.get("byDay").size() >= 1);
        // views drive the menu queues
        assertEquals(1, call(get(API + "/returns?view=NEW"), admin(A), null, 200).get("total").asInt());
        assertEquals(1, call(get(API + "/returns?view=APPROVED"), admin(A), null, 200).get("total").asInt());
        assertEquals(1, call(get(API + "/returns?view=REJECTED"), admin(A), null, 200).get("total").asInt());
        assertEquals(0, call(get(API + "/returns?view=IN_TRANSIT"), admin(A), null, 200).get("total").asInt());
        call(get(API + "/returns?view=BOGUS"), admin(A), null, 400);
        assertEquals(1, call(get(API + "/returns?q=RMA-" + String.format("%06d", a)), admin(A), null, 200).get("total").asInt());
        assertEquals(3, call(get(API + "/returns?q=alice"), admin(A), null, 200).get("total").asInt());
        assertEquals(0, call(get(API + "/returns?q=%25"), admin(A), null, 200).get("total").asInt(), "LIKE wildcards are escaped");
        MvcResult csv = mvc.perform(get(API + "/returns/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin(A))).andExpect(status().isOk()).andReturn();
        assertTrue(csv.getResponse().getContentAsString().startsWith("rma,order,status"));
        assertEquals(4, csv.getResponse().getContentAsString().split("\n").length);
        call(get(API + "/returns/export"), without(A, "return:export"), null, 403);
    }
}

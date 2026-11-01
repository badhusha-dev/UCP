package com.ecommerce.inventory.ops;

import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.model.InventoryItem;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.inventory.security.InventoryAuthorizationClient;
import com.ecommerce.inventory.service.InventoryProducerService;
import com.ecommerce.inventory.service.InventoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "SERVICE_TO_SERVICE_TOKEN=test-service-token")
class OpsIntegrationTest {
    static final UUID A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static final UUID B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    static final Set<String> ALL = Set.of("inventory:view", "inventory:adjust", "inventory:adjust-approve", "inventory:transfer", "inventory:transfer-approve",
            "inventory:audit", "inventory:config", "inventory:export", "warehouse:view", "warehouse:manage");

    private static final String PG_URL = System.getenv("UCP_TEST_INVENTORY_POSTGRES_URL");

    @org.springframework.test.context.DynamicPropertySource
    static void optionalPostgres(org.springframework.test.context.DynamicPropertyRegistry p) {
        if (PG_URL == null || PG_URL.isBlank()) return;
        p.add("spring.datasource.url", () -> PG_URL);
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("UCP_TEST_INVENTORY_POSTGRES_USERNAME", "postgres"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("UCP_TEST_INVENTORY_POSTGRES_PASSWORD", ""));
        p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired InventoryItemRepository items;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @MockBean InventoryProducerService producer;
    @MockBean InventoryAuthorizationClient authorization;
    @Value("${jwt.secret}") String secret;

    @BeforeEach
    void clean() {
        for (String t : List.of("wms_transfer_line", "wms_transfer", "wms_adjustment", "wms_cost_layer", "wms_stock", "wms_location", "wms_warehouse", "wms_audit_log", "wms_config",
                "stock_reservation", "stock_movement", "inventory_item")) jdbc.update("DELETE FROM " + t);
    }

    String token(UUID tenant, String email, Set<String> perms) {
        Date now = new Date();
        return "Bearer " + Jwts.builder().setClaims(Map.of("id", 7L, "role", "STAFF", "tenant_role", "STAFF", "tenant_id", tenant.toString(), "application_code", "INVENTORY", "permissions", perms))
                .setSubject(email).setId(UUID.randomUUID().toString()).setIssuer("universal-commerce").setAudience("commerce-api").setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 600_000)).signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256).compact();
    }

    JsonNode call(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }

    ResultActions post(String url, String auth, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    long warehouse(UUID tenant, String code) throws Exception {
        return call(post("/api/inventory/ops/warehouses", token(tenant, "mgr@a.test", ALL), Map.of("code", code, "name", "WH " + code, "capacityUnits", 1000)).andExpect(status().isOk())).get("id").asLong();
    }

    void receive(UUID tenant, long wh, long product, int qty, String cost, String ref) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/inventory/receipts").header("X-Service-Token", "test-service-token").header("X-Tenant-Id", tenant.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceRef", ref, "warehouseId", wh, "lines", List.of(Map.of("productId", product, "acceptedQuantity", qty, "unitCost", cost)))))).andExpect(status().isOk());
    }

    @Test void availabilityExcludesReservedUnavailableAndIncoming() {
        items.save(InventoryItem.builder().tenantId(A).productId(1L).stockQuantity(100).reservedQuantity(20).unavailableQuantity(10).incomingQuantity(500).build());
        assertEquals(70, items.findByTenantIdAndProductId(A, 1L).orElseThrow().getAvailableQuantity());
    }

    @Test void warehouseIsTenantIsolatedAndRbacEnforced() throws Exception {
        long wh = warehouse(A, "MAIN");
        mvc.perform(get("/api/inventory/ops/warehouses").header("Authorization", token(B, "x@b.test", ALL))).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(0));
        post("/api/inventory/ops/warehouses/" + wh + "/locations", token(B, "x@b.test", ALL), Map.of("code", "A1")).andExpect(status().isNotFound());
        post("/api/inventory/ops/warehouses", token(A, "viewer@a.test", Set.of("warehouse:view")), Map.of("code", "X", "name", "X")).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/ops/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inventory/ops/dashboard").header("Authorization", token(A, "n@a.test", Set.of("warehouse:view")))).andExpect(status().isForbidden());
        post("/api/inventory/ops/warehouses", token(A, "mgr@a.test", ALL), Map.of("code", "main", "name", "dup")).andExpect(status().isConflict());
    }

    @Test void transferWorkflowMovesStockOnlyAtDispatchAndAcceptance() throws Exception {
        long src = warehouse(A, "SRC"), dst = warehouse(A, "DST");
        receive(A, src, 5L, 50, "2.00", "GR-1");
        String requester = token(A, "req@a.test", ALL), approver = token(A, "boss@a.test", ALL);
        long id = call(post("/api/inventory/ops/transfers", requester, Map.of("sourceWarehouseId", src, "destinationWarehouseId", dst, "lines", List.of(Map.of("productId", 5, "quantity", 20)))).andExpect(status().isOk())).get("id").asLong();
        post("/api/inventory/ops/transfers/" + id + "/approve", requester, Map.of()).andExpect(status().isConflict()); // DRAFT cannot be approved
        post("/api/inventory/ops/transfers/" + id + "/request", requester, Map.of()).andExpect(status().isOk());
        post("/api/inventory/ops/transfers/" + id + "/approve", requester, Map.of()).andExpect(status().isForbidden()); // requester cannot self-approve
        post("/api/inventory/ops/transfers/" + id + "/approve", approver, Map.of()).andExpect(status().isOk());
        InventoryItem item = items.findByTenantIdAndProductId(A, 5L).orElseThrow();
        assertEquals(20, item.getReservedQuantity()); // source reserved
        assertEquals(30, item.getAvailableQuantity());
        post("/api/inventory/ops/transfers/" + id + "/pick", requester, Map.of()).andExpect(status().isOk());
        post("/api/inventory/ops/transfers/" + id + "/dispatch", requester, Map.of()).andExpect(status().isOk());
        item = items.findByTenantIdAndProductId(A, 5L).orElseThrow();
        assertEquals(30, item.getStockQuantity());
        assertEquals(0, item.getReservedQuantity());
        assertEquals(0, jdbc.queryForObject("SELECT COALESCE(SUM(on_hand),0) FROM wms_stock WHERE warehouse_id=?", Integer.class, dst)); // destination untouched until accepted
        post("/api/inventory/ops/transfers/" + id + "/receive", requester, Map.of("receive", List.of(Map.of("productId", 5, "received", 12, "damaged", 2)))).andExpect(status().isOk());
        assertEquals(10, jdbc.queryForObject("SELECT on_hand FROM wms_stock WHERE warehouse_id=?", Integer.class, dst));
        assertEquals("PARTIALLY_RECEIVED", jdbc.queryForObject("SELECT status FROM wms_transfer WHERE id=?", String.class, id));
        post("/api/inventory/ops/transfers/" + id + "/receive", requester, Map.of("receive", List.of(Map.of("productId", 5, "received", 8)))).andExpect(status().isOk());
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM wms_transfer WHERE id=?", String.class, id));
        post("/api/inventory/ops/transfers/" + id + "/receive", requester, Map.of("receive", List.of(Map.of("productId", 5, "received", 1)))).andExpect(status().isConflict());
    }

    @Test void adjustmentAboveThresholdNeedsDifferentApproverAndKeepsBeforeAfter() throws Exception {
        long wh = warehouse(A, "ADJ");
        receive(A, wh, 9L, 100, "1.00", "GR-ADJ");
        String requester = token(A, "clerk@a.test", ALL);
        JsonNode small = call(post("/api/inventory/ops/adjustments", requester, Map.of("warehouseId", wh, "productId", 9, "newQuantity", 95, "reason", "Damaged on shelf", "evidence", "photo-1")).andExpect(status().isOk()));
        assertEquals("APPLIED", small.get("status").asText());
        assertEquals(100, small.get("beforeQuantity").asInt());
        post("/api/inventory/ops/adjustments", requester, Map.of("warehouseId", wh, "productId", 9, "newQuantity", 10)).andExpect(status().isBadRequest()); // reason mandatory
        JsonNode big = call(post("/api/inventory/ops/adjustments", requester, Map.of("warehouseId", wh, "productId", 9, "newQuantity", 40, "reason", "Count mismatch", "idempotencyKey", "k1")).andExpect(status().isOk()));
        assertEquals("PENDING", big.get("status").asText());
        assertEquals(95, items.findByTenantIdAndProductId(A, 9L).orElseThrow().getStockQuantity()); // not applied yet
        assertEquals(big.get("id").asLong(), call(post("/api/inventory/ops/adjustments", requester, Map.of("warehouseId", wh, "productId", 9, "newQuantity", 40, "reason", "Count mismatch", "idempotencyKey", "k1"))).get("id").asLong());
        post("/api/inventory/ops/adjustments/" + big.get("id").asLong() + "/approve", requester, Map.of()).andExpect(status().isForbidden());
        post("/api/inventory/ops/adjustments/" + big.get("id").asLong() + "/approve", token(A, "boss@a.test", ALL), Map.of()).andExpect(status().isOk());
        assertEquals(40, items.findByTenantIdAndProductId(A, 9L).orElseThrow().getStockQuantity());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wms_adjustment", Integer.class));
    }

    @Test void simultaneousReservationsNeverOversell() throws Exception {
        items.save(InventoryItem.builder().tenantId(A).productId(77L).stockQuantity(10).build());
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < 30; i++) fs.add(pool.submit(() -> {
            go.await();
            try { inventory.reserveStock(A, ReserveStockRequest.builder().productId(77L).quantity(1).build()); ok.incrementAndGet(); } catch (Exception ignored) { }
            return null;
        }));
        go.countDown();
        for (var f : fs) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        InventoryItem item = items.findByTenantIdAndProductId(A, 77L).orElseThrow();
        assertEquals(10, ok.get());
        assertEquals(10, item.getReservedQuantity());
        assertEquals(0, item.getAvailableQuantity());
    }

    @Test void reservationIdempotencyPartialAllocationAndRelease() {
        items.save(InventoryItem.builder().tenantId(A).productId(88L).stockQuantity(5).build());
        var first = inventory.reserveStock(A, ReserveStockRequest.builder().productId(88L).quantity(3).idempotencyKey("order-1").build());
        var replay = inventory.reserveStock(A, ReserveStockRequest.builder().productId(88L).quantity(3).idempotencyKey("order-1").build());
        assertEquals(first.getReservationId(), replay.getReservationId());
        assertEquals(3, items.findByTenantIdAndProductId(A, 88L).orElseThrow().getReservedQuantity());
        assertThrows(Exception.class, () -> inventory.reserveStock(A, ReserveStockRequest.builder().productId(88L).quantity(4).build()));
        var partial = inventory.reserveStock(A, ReserveStockRequest.builder().productId(88L).quantity(4).allowPartial(true).build());
        assertEquals(2, partial.getQuantity());
        assertEquals(2, jdbc.queryForObject("SELECT backorder_quantity FROM stock_reservation WHERE reservation_id=?", Integer.class, partial.getReservationId()) - 0);
        inventory.releaseReservation(A, first.getReservationId());
        inventory.releaseReservation(A, first.getReservationId()); // double release is harmless
        assertEquals(2, items.findByTenantIdAndProductId(A, 88L).orElseThrow().getReservedQuantity());
        assertThrows(Exception.class, () -> inventory.reserveStock(B, ReserveStockRequest.builder().productId(88L).quantity(1).build())); // other tenant has no such item
    }

    @Test void receivingIsIdempotentIncludingConcurrentDuplicates() throws Exception {
        long wh = warehouse(A, "RCV");
        receive(A, wh, 31L, 40, "3.00", "GR-DUP");
        receive(A, wh, 31L, 40, "3.00", "GR-DUP"); // duplicate delivery of the same receipt
        assertEquals(40, items.findByTenantIdAndProductId(A, 31L).orElseThrow().getStockQuantity());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        OpsService.ReceiptRequest req = new OpsService.ReceiptRequest("GR-RACE", wh, List.of(new OpsService.ReceiptLine(31L, 10, new BigDecimal("4.00"), null, null)));
        for (int i = 0; i < 8; i++) fs.add(pool.submit(() -> { go.await(); try { inventoryOps().receive(A, req, "t"); } catch (Exception ignored) { } return null; }));
        go.countDown();
        for (var f : fs) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(50, items.findByTenantIdAndProductId(A, 31L).orElseThrow().getStockQuantity());
    }

    @Autowired OpsService opsService;
    OpsService inventoryOps() { return opsService; }

    @Test void internalCallbacksFailClosedWithoutServiceTokenOrTenant() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/inventory/receipts").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/inventory/receipts").header("X-Service-Token", "wrong").header("X-Tenant-Id", A.toString()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/inventory/receipts").header("X-Service-Token", "test-service-token").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test void supplierReturnConsumesFifoLayersAndValuationReconciles() throws Exception {
        long wh = warehouse(A, "VAL");
        receive(A, wh, 41L, 10, "2.00", "GR-V1");
        Thread.sleep(5);
        receive(A, wh, 41L, 10, "3.00", "GR-V2");
        JsonNode r = json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/inventory/supplier-returns").header("X-Service-Token", "test-service-token").header("X-Tenant-Id", A.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sourceRef", "SR-1", "warehouseId", wh, "productId", 41, "quantity", 15)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(0, new BigDecimal("35").compareTo(r.get("costValue").decimalValue()));
        JsonNode val = call(mvc.perform(get("/api/inventory/ops/valuation").header("Authorization", token(A, "a@a.test", ALL))).andExpect(status().isOk()));
        assertEquals(0, new BigDecimal("15").compareTo(val.get("fifoTotal").decimalValue())); // 5 units left at 3.00
        assertEquals(5, items.findByTenantIdAndProductId(A, 41L).orElseThrow().getStockQuantity());
    }

    @Test void forecastUsesRealMovementHistoryOnly() throws Exception {
        long wh = warehouse(A, "FC");
        receive(A, wh, 51L, 100, "1.00", "GR-F");
        receive(A, wh, 52L, 10, "1.00", "GR-F2");
        jdbc.update("INSERT INTO stock_movement(tenant_id, product_id, delta, movement_type, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)", A, 51L, -60, "ORDER_DEDUCTION");
        JsonNode rows = call(mvc.perform(get("/api/inventory/ops/forecast?windowDays=30").header("Authorization", token(A, "a@a.test", ALL))).andExpect(status().isOk()));
        Map<Long, JsonNode> by = new HashMap<>();
        rows.forEach(n -> by.put(n.get("productId").asLong(), n));
        assertEquals(0, new BigDecimal("2").compareTo(by.get(51L).get("avgDailyDemand").decimalValue()));
        assertEquals("ACTIVE", by.get(51L).get("classification").asText());
        assertEquals("DEAD_STOCK", by.get(52L).get("classification").asText());
    }

    @Test void dashboardAndInventoryListHonourFiltersAndCsvIsSafe() throws Exception {
        items.save(InventoryItem.builder().tenantId(A).productId(61L).sku("=CMD()").stockQuantity(0).build());
        items.save(InventoryItem.builder().tenantId(A).productId(62L).sku("OK-1").stockQuantity(500).build());
        items.save(InventoryItem.builder().tenantId(B).productId(63L).sku("OTHER").stockQuantity(5).build());
        String auth = token(A, "a@a.test", ALL);
        JsonNode dash = call(mvc.perform(get("/api/inventory/ops/dashboard").header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals(2, dash.get("skus").asInt());
        assertEquals(1, dash.get("outOfStock").asInt());
        JsonNode out = call(mvc.perform(get("/api/inventory/ops/items?status=OUT_OF_STOCK").header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals(1, out.get("total").asInt());
        String csv = mvc.perform(get("/api/inventory/ops/export").header("Authorization", auth)).andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("\"'=CMD()\""));
        assertFalse(csv.contains("OTHER"));
    }
}

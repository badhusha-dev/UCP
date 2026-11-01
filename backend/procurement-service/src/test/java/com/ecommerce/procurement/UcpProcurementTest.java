package com.ecommerce.procurement.ucp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UcpProcurementTest {
    static final UUID A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    static final UUID B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    static final Set<String> ALL = Set.of("supplier:view", "supplier:manage", "purchase-order:view", "purchase-order:manage", "purchase-order:approve", "goods-receipt:manage", "supplier-return:manage", "inventory:audit");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean InventoryClient inventory;
    @MockBean AccountingClient accounting;
    @MockBean UcpAuthorizationClient authorization;
    @Value("${jwt.secret}") String secret;

    @BeforeEach
    void clean() {
        for (String t : List.of("proc_receipt_line", "proc_receipt", "proc_return_line", "proc_return", "proc_po_line", "proc_po", "proc_supplier_item", "proc_supplier", "proc_audit")) jdbc.update("DELETE FROM " + t);
        reset(inventory, accounting);
        when(inventory.receipt(any(), anyMap())).thenReturn(Map.of("status", "RECEIVED"));
        when(inventory.incoming(any(), anyLong(), anyInt())).thenReturn(Map.of("status", "OK"));
    }

    String token(UUID tenant, String email, Set<String> perms, String app) {
        Date now = new Date();
        return "Bearer " + Jwts.builder().setClaims(Map.of("id", 3L, "tenant_role", "STAFF", "tenant_id", tenant.toString(), "application_code", app, "permissions", perms))
                .setSubject(email).setId(UUID.randomUUID().toString()).setIssuer("universal-commerce").setAudience("commerce-api").setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 600_000)).signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256).compact();
    }
    String tok(UUID tenant, String email) { return token(tenant, email, ALL, "INVENTORY"); }

    ResultActions send(String method, String url, String auth, Object body, String idem) throws Exception {
        var b = method.equals("GET") ? MockMvcRequestBuilders.get(url) : MockMvcRequestBuilders.post(url);
        b.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON);
        if (idem != null) b.header("Idempotency-Key", idem);
        if (body != null) b.content(json.writeValueAsString(body));
        return mvc.perform(b);
    }
    JsonNode ok(ResultActions r) throws Exception { return json.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }

    long approvedSupplier(UUID tenant, String code) throws Exception {
        long id = ok(send("POST", "/api/procurement/ucp/suppliers", tok(tenant, "buyer@x.test"), Map.of("code", code, "name", "Supplier " + code), null)).get("id").asLong();
        send("POST", "/api/procurement/ucp/suppliers/" + id + "/approve", tok(tenant, "buyer@x.test"), null, null).andExpect(status().isOk());
        return id;
    }

    long issuedPo(UUID tenant, long supplier, int qty, String expected) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("supplierId", supplier, "warehouseId", 1, "discount", qty >= 100 ? 100 : 1, "taxRatePercent", 18, "shipping", 2,
                "lines", List.of(Map.of("productId", 10, "quantity", qty, "unitCost", 10))));
        if (expected != null) body.put("expectedDate", expected);
        long po = ok(send("POST", "/api/procurement/ucp/purchase-orders", tok(tenant, "buyer@x.test"), body, null)).get("id").asLong();
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/submit", tok(tenant, "buyer@x.test"), null, null).andExpect(status().isOk());
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/approve", tok(tenant, "boss@x.test"), null, null).andExpect(status().isOk());
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/issue", tok(tenant, "buyer@x.test"), null, null).andExpect(status().isOk());
        return po;
    }

    long lineId(long po) { return jdbc.queryForObject("SELECT id FROM proc_po_line WHERE po_id=?", Long.class, po); }

    @Test void poRulesAndLifecycleGuards() {
        assertTrue(PoRules.State.DRAFT.canMoveTo(PoRules.State.PENDING_APPROVAL));
        assertFalse(PoRules.State.DRAFT.canMoveTo(PoRules.State.ISSUED));
        assertFalse(PoRules.State.RECEIVED.canMoveTo(PoRules.State.CANCELLED));
        assertEquals(new BigDecimal("1064.0000"), PoRules.total(new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("18"), new BigDecimal("2")));
        assertEquals(8, PoRules.accepted(10, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> PoRules.accepted(5, 3, 3));
    }

    @Test void purchaseOrderWorkflowTotalsApprovalAndReceiving() throws Exception {
        long sup = approvedSupplier(A, "ACME");
        long po = issuedPo(A, sup, 100, null);
        JsonNode view = ok(send("GET", "/api/procurement/ucp/purchase-orders/" + po, tok(A, "v@x.test"), null, null));
        assertEquals(0, new BigDecimal("1064").compareTo(view.get("total").decimalValue())); // (1000-100)*1.18+2
        verify(inventory).incoming(eq(A), eq(10L), eq(100));
        long line = lineId(po);
        JsonNode r = ok(send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/receipts", tok(A, "wh@x.test"), Map.of("lines", List.of(Map.of("poLineId", line, "received", 60, "rejected", 5, "damaged", 5, "batchNo", "B1"))), "rcpt-1"));
        assertEquals("PARTIALLY_RECEIVED", r.get("poStatus").asText());
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(inventory).receipt(eq(A), cap.capture());
        assertEquals(50, ((Map<?, ?>) ((List<?>) cap.getValue().get("lines")).get(0)).get("acceptedQuantity")); // only accepted quantity reaches stock
        verify(inventory).incoming(eq(A), eq(10L), eq(-10)); // rejected + damaged stop being "incoming"
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/receipts", tok(A, "wh@x.test"), Map.of("lines", List.of(Map.of("poLineId", line, "received", 60))), "rcpt-2").andExpect(status().isConflict()); // 50+60 > 100 + 5% tolerance
        JsonNode done = ok(send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/receipts", tok(A, "wh@x.test"), Map.of("lines", List.of(Map.of("poLineId", line, "received", 50))), "rcpt-3"));
        assertEquals("RECEIVED", done.get("poStatus").asText());
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/cancel", tok(A, "buyer@x.test"), null, null).andExpect(status().isConflict());
    }

    @Test void approvalSeparationAndSupplierGate() throws Exception {
        long pending = ok(send("POST", "/api/procurement/ucp/suppliers", tok(A, "buyer@x.test"), Map.of("code", "NEW", "name", "New"), null)).get("id").asLong();
        send("POST", "/api/procurement/ucp/purchase-orders", tok(A, "buyer@x.test"), Map.of("supplierId", pending, "warehouseId", 1, "lines", List.of(Map.of("productId", 1, "quantity", 1, "unitCost", 1))), null).andExpect(status().isConflict());
        long sup = approvedSupplier(A, "OK");
        long po = ok(send("POST", "/api/procurement/ucp/purchase-orders", tok(A, "buyer@x.test"), Map.of("supplierId", sup, "warehouseId", 1, "lines", List.of(Map.of("productId", 1, "quantity", 2, "unitCost", 5))), null)).get("id").asLong();
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/approve", tok(A, "buyer@x.test"), null, null).andExpect(status().isConflict()); // DRAFT cannot be approved
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/submit", tok(A, "buyer@x.test"), null, null).andExpect(status().isOk());
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/approve", tok(A, "buyer@x.test"), null, null).andExpect(status().isForbidden()); // creator cannot approve
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/reject", tok(A, "boss@x.test"), Map.of(), null).andExpect(status().isBadRequest()); // reason required
        send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/reject", tok(A, "boss@x.test"), Map.of("reason", "too expensive"), null).andExpect(status().isOk());
    }

    @Test void duplicateAndConcurrentReceiptsAddStockOnce() throws Exception {
        long sup = approvedSupplier(A, "DUP");
        long po = issuedPo(A, sup, 100, null);
        long line = lineId(po);
        Map<String, Object> body = Map.of("lines", List.of(Map.of("poLineId", line, "received", 10)));
        ok(send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/receipts", tok(A, "wh@x.test"), body, "same-key"));
        JsonNode replay = ok(send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/receipts", tok(A, "wh@x.test"), body, "same-key"));
        assertTrue(replay.get("replayed").asBoolean());
        verify(inventory, times(1)).receipt(any(), anyMap());
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 6; i++) fs.add(pool.submit(() -> { go.await(); return send("POST", "/api/procurement/ucp/purchase-orders/" + po + "/receipts", tok(A, "wh@x.test"), body, "race-key").andReturn().getResponse().getStatus(); }));
        go.countDown();
        for (var f : fs) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        verify(inventory, times(2)).receipt(any(), anyMap()); // one for same-key, exactly one for race-key
        assertEquals(20, jdbc.queryForObject("SELECT accepted_quantity FROM proc_po_line WHERE id=?", Integer.class, line));
    }

    @Test void overdueIsDerivedFromExpectedDate() throws Exception {
        long sup = approvedSupplier(A, "LATE");
        long late = issuedPo(A, sup, 5, LocalDate.now().minusDays(3).toString());
        issuedPo(A, sup, 5, LocalDate.now().plusDays(10).toString());
        JsonNode overdue = ok(send("GET", "/api/procurement/ucp/purchase-orders?status=OVERDUE", tok(A, "v@x.test"), null, null));
        assertEquals(1, overdue.size());
        assertEquals(late, overdue.get(0).get("id").asLong());
    }

    @Test void supplierReturnRemovesStockThenPostsCreditNoteThroughAccountingApiOnce() throws Exception {
        long sup = approvedSupplier(A, "RET");
        when(inventory.supplierReturn(eq(A), anyMap())).thenReturn(Map.of("costValue", "35.0000"));
        when(accounting.creditNote(anyMap())).thenReturn(Map.of("entryNumber", "APC-1"));
        long id = ok(send("POST", "/api/procurement/ucp/supplier-returns", tok(A, "wh@x.test"), Map.of("supplierId", sup, "warehouseId", 1, "reason", "Defective", "lines", List.of(Map.of("productId", 10, "quantity", 15))), null)).get("id").asLong();
        send("POST", "/api/procurement/ucp/supplier-returns/" + id + "/credit", tok(A, "wh@x.test"), null, null).andExpect(status().isConflict()); // not shipped yet
        ok(send("POST", "/api/procurement/ucp/supplier-returns/" + id + "/ship", tok(A, "wh@x.test"), null, null));
        send("POST", "/api/procurement/ucp/supplier-returns/" + id + "/ship", tok(A, "wh@x.test"), null, null).andExpect(status().isConflict());
        JsonNode credited = ok(send("POST", "/api/procurement/ucp/supplier-returns/" + id + "/credit", tok(A, "wh@x.test"), null, null));
        ok(send("POST", "/api/procurement/ucp/supplier-returns/" + id + "/credit", tok(A, "wh@x.test"), null, null));
        assertEquals("CREDITED", credited.get("status").asText());
        assertEquals(0, new BigDecimal("35").compareTo(credited.get("total_credit").decimalValue()));
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(accounting, times(1)).creditNote(cap.capture());
        assertTrue(String.valueOf(cap.getValue().get("idempotencyKey")).startsWith("SRET-" + A));
    }

    @Test void tenantIsolationRbacAndTokenChecks() throws Exception {
        long sup = approvedSupplier(A, "ISO");
        send("GET", "/api/procurement/ucp/suppliers/" + sup + "/items", tok(B, "b@x.test"), null, null).andExpect(status().isNotFound());
        assertEquals(0, ok(send("GET", "/api/procurement/ucp/suppliers", tok(B, "b@x.test"), null, null)).size());
        send("POST", "/api/procurement/ucp/purchase-orders", tok(B, "b@x.test"), Map.of("supplierId", sup, "warehouseId", 1, "lines", List.of(Map.of("productId", 1, "quantity", 1, "unitCost", 1))), null).andExpect(status().isNotFound());
        send("POST", "/api/procurement/ucp/suppliers", token(A, "ro@x.test", Set.of("supplier:view"), "INVENTORY"), Map.of("code", "X", "name", "X"), null).andExpect(status().isForbidden());
        send("GET", "/api/procurement/ucp/suppliers", token(A, "ro@x.test", ALL, "DELIVERY_OPS"), null, null).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/procurement/ucp/suppliers")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/procurement/ucp/suppliers").header("X-Tenant-Id", B.toString()).header("Authorization", tok(A, "a@x.test"))).andExpect(status().isOk()); // header is ignored; token tenant wins
        assertEquals(1, ok(send("GET", "/api/procurement/ucp/suppliers", tok(A, "a@x.test"), null, null)).size());
    }

    @Test void replenishmentSuggestionBecomesDraftOnly() throws Exception {
        long sup = approvedSupplier(A, "REP");
        send("POST", "/api/procurement/ucp/suppliers/" + sup + "/items", tok(A, "b@x.test"), Map.of("productId", 10, "unitCost", 4, "leadTimeDays", 5), null).andExpect(status().isOk());
        when(inventory.forecast(A)).thenReturn(List.of(Map.of("productId", 10, "suggestedOrderQuantity", 40), Map.of("productId", 11, "suggestedOrderQuantity", 0)));
        JsonNode s = ok(send("GET", "/api/procurement/ucp/replenishment", tok(A, "b@x.test"), null, null));
        assertEquals(1, s.size());
        assertEquals(sup, s.get(0).get("suggestedSupplier").get("supplier_id").asLong());
        JsonNode draft = ok(send("POST", "/api/procurement/ucp/replenishment/draft-po", tok(A, "b@x.test"), Map.of("supplierId", sup, "warehouseId", 1, "lines", List.of(Map.of("productId", 10, "quantity", 40))), null));
        assertEquals("DRAFT", draft.get("status").asText());
        verify(inventory, never()).incoming(any(), anyLong(), anyInt());
    }
}

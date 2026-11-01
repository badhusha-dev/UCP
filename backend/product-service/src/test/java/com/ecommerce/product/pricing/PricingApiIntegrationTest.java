package com.ecommerce.product.pricing;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.security.CatalogAuthorizationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs the real V7 migration text on H2 (PostgreSQL mode) and exercises the whole Pricing & Promotions API over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"SERVICE_TO_SERVICE_TOKEN=test-service-token", "pricing.scheduler.delay-ms=3600000"})
class PricingApiIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");
    private static final List<String> ALL = List.of("pricing:read", "pricing:create", "pricing:update", "pricing:approve", "pricing:publish",
            "pricing:cost:read", "pricing:export", "pricing:audit", "promotion:read", "promotion:create", "promotion:update", "promotion:approve", "coupon:manage");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProductRepository products;
    @Autowired private PriceService priceService;
    @MockBean private CatalogAuthorizationClient grants;
    @Value("${jwt.secret}") private String secret;

    private static final String BASE = "/api/products/admin/pricing";

    @BeforeEach
    void reset() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__pricing_and_promotions.sql")).execute(dataSource);
        for (String t : List.of("promotion_order_applications", "coupon_redemptions", "coupon_codes", "promotion_rewards", "promotion_conditions",
                "promotion_rules", "promotion_approvals", "promotion_campaigns", "scheduled_prices", "price_history", "product_prices",
                "promotion_audit_logs", "pricing_settings")) jdbc.update("DELETE FROM " + t);
        products.deleteAll();
        doNothing().when(grants).requireCurrentGrant(anyString());
    }

    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        reset(); // leave the shared in-memory database empty for other test classes
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private String token(UUID tenant, long userId, List<String> permissions) {
        return Jwts.builder().setSubject("user" + userId + "@" + tenant).setId(UUID.randomUUID().toString()).setIssuer("universal-commerce")
                .setAudience("commerce-api").setExpiration(new Date(System.currentTimeMillis() + 600_000)).claim("id", userId)
                .claim("tenant_id", tenant.toString()).claim("tenant_role", "PRICING_MANAGER").claim("application_code", "ECOMMERCE_ADMIN")
                .claim("permissions", permissions).signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
    }
    private String maker(UUID t) { return token(t, 1, ALL); }
    private String checker(UUID t) { return token(t, 2, ALL); }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) { return b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token); }

    private JsonNode send(MockHttpServletRequestBuilder b, String token, Object body, int status) throws Exception {
        MockHttpServletRequestBuilder r = auth(b, token);
        if (body != null) r = r.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        MvcResult res = mvc.perform(r).andExpect(status().is(status)).andReturn();
        String content = res.getResponse().getContentAsString();
        return content.isBlank() ? null : json.readTree(content);
    }

    private long product(UUID tenant, String name, double price, String cost) {
        Product p = Product.builder().tenantId(tenant).name(name).price(price).stockQuantity(100)
                .costPrice(cost == null ? null : new BigDecimal(cost)).build();
        return products.save(p).getId();
    }

    private Map<String, Object> price(long productId, String base, String sale) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", productId); m.put("currency", "USD"); m.put("basePrice", new BigDecimal(base));
        if (sale != null) m.put("salePrice", new BigDecimal(sale));
        return m;
    }

    private Map<String, Object> campaign(String name, String type, String scope, long productId, Map<String, Object> reward) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", name); c.put("promotionType", type); c.put("scope", scope); c.put("currency", "USD");
        c.put("startsAt", OffsetDateTime.now().minusHours(1).toString()); c.put("endsAt", OffsetDateTime.now().plusDays(7).toString());
        c.put("rules", List.of(Map.of("targetType", "PRODUCT", "targetId", productId)));
        c.put("rewards", List.of(reward));
        return c;
    }

    private Map<String, Object> percentReward(String pct) { return Map.of("type", "PERCENT_OFF", "percent", new BigDecimal(pct)); }

    /** Creates, submits, approves (by a different user) and publishes a campaign; returns its id. */
    private long liveCampaign(UUID tenant, Map<String, Object> request) throws Exception {
        long id = send(post(BASE + "/campaigns"), maker(tenant), request, 201).get("id").asLong();
        send(post(BASE + "/campaigns/" + id + "/submit"), maker(tenant), null, 200);
        if ("PENDING_APPROVAL".equals(send(get(BASE + "/campaigns/" + id), maker(tenant), null, 200).get("status").asText())) {
            send(post(BASE + "/campaigns/" + id + "/decision"), checker(tenant), Map.of("approve", true, "note", "ok"), 200);
        }
        send(post(BASE + "/campaigns/" + id + "/publish"), maker(tenant), null, 200);
        return id;
    }

    private Map<String, Object> quoteBody(long productId, int qty, String coupon, String customer, String orderRef) {
        Map<String, Object> m = new HashMap<>();
        m.put("currency", "USD");
        m.put("lines", List.of(Map.of("productId", productId, "quantity", qty)));
        m.put("customer", Map.of("id", customer));
        if (coupon != null) m.put("couponCode", coupon);
        if (orderRef != null) m.put("orderRef", orderRef);
        return m;
    }

    private JsonNode internal(String path, UUID tenant, Object body, int status) throws Exception {
        MockHttpServletRequestBuilder b = post("/internal/pricing/" + path).header("X-Service-Token", "test-service-token").header("X-Tenant-Id", tenant.toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body == null ? Map.of() : body));
        MvcResult res = mvc.perform(b).andExpect(status().is(status)).andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    // ---- authentication & permissions --------------------------------------------------------------------------

    @Test @DisplayName("unauthenticated, wrong-application and permission-less callers are rejected")
    void authAndPermissions() throws Exception {
        mvc.perform(get(BASE + "/dashboard")).andExpect(status().isUnauthorized());
        String readOnly = token(TENANT_A, 5, List.of("pricing:read"));
        send(get(BASE + "/dashboard"), readOnly, null, 200);
        send(post(BASE + "/prices"), readOnly, price(1, "10.00", null), 403);
        send(get(BASE + "/audit"), readOnly, null, 403);
        send(get(BASE + "/campaigns"), readOnly, null, 403);
        send(post(BASE + "/coupons"), readOnly, Map.of("campaignId", 1), 403);
        mvc.perform(get(BASE + "/prices/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + readOnly)).andExpect(status().isForbidden());
    }

    // ---- prices -----------------------------------------------------------------------------------------------

    @Test @DisplayName("price create/update validates values and records history; cost price needs its own permission")
    void priceManagement() throws Exception {
        long p = product(TENANT_A, "Widget", 20, "5.00");
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "-1", null), 400);
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "0", null), 400);
        Map<String, Object> bad = price(p, "10.00", null); bad.put("currency", "ZZZ");
        send(post(BASE + "/prices"), maker(TENANT_A), bad, 400);
        bad.put("currency", "XYZ1"); send(post(BASE + "/prices"), maker(TENANT_A), bad, 400);
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "10.00", "12.00"), 400); // sale above base

        JsonNode created = send(post(BASE + "/prices"), maker(TENANT_A), price(p, "30.00", "25.00"), 201);
        assertEquals(0, new BigDecimal("30.00").compareTo(created.get("basePrice").decimalValue()));
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "31.00", null), 409); // already exists -> use PUT

        Map<String, Object> update = price(p, "32.00", null); update.put("expectedVersion", 0);
        send(put(BASE + "/prices"), maker(TENANT_A), update, 200);
        update.put("expectedVersion", 0); // stale
        send(put(BASE + "/prices"), maker(TENANT_A), update, 409);
        send(put(BASE + "/prices"), token(TENANT_A, 8, List.of("pricing:update")), price(p, "50.00", null), 403); // >20% jump needs publish
        send(put(BASE + "/prices"), token(TENANT_A, 9, List.of("pricing:update", "pricing:publish")), price(p, "50.00", null), 200);

        JsonNode history = send(get(BASE + "/prices/history?productId=" + p), maker(TENANT_A), null, 200);
        assertEquals(3, history.size());
        assertEquals("MANUAL", history.get(0).get("changeSource").asText());

        // below-cost price violates the minimum margin
        Map<String, Object> loss = price(p, "4.00", null);
        send(put(BASE + "/prices"), maker(TENANT_A), loss, 400);

        // restricted cost visibility
        String noCost = token(TENANT_A, 3, List.of("pricing:read", "pricing:update"));
        JsonNode list = send(get(BASE + "/prices"), noCost, null, 200);
        assertFalse(list.get(0).has("costPrice"));
        assertTrue(send(get(BASE + "/prices"), maker(TENANT_A), null, 200).get(0).has("costPrice"));
        Map<String, Object> withCost = price(p, "50.00", null); withCost.put("costPrice", new BigDecimal("6.00"));
        send(put(BASE + "/prices"), noCost, withCost, 403);
    }

    @Test @DisplayName("rollback restores the previous price and keeps the audit trail")
    void rollback() throws Exception {
        long p = product(TENANT_A, "Rollbackable", 20, null);
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "30.00", null), 201);
        send(put(BASE + "/prices"), maker(TENANT_A), price(p, "32.00", null), 200);
        long historyId = send(get(BASE + "/prices/history?productId=" + p), maker(TENANT_A), null, 200).get(0).get("id").asLong();
        send(post(BASE + "/prices/history/" + historyId + "/rollback"), maker(TENANT_A), null, 200);
        assertEquals(0, new BigDecimal("30.00").compareTo(send(get(BASE + "/prices?productId=" + p), maker(TENANT_A), null, 200).get(0).get("basePrice").decimalValue()));
        assertEquals(3, send(get(BASE + "/prices/history?productId=" + p), maker(TENANT_A), null, 200).size());
    }

    @Test @DisplayName("bulk update supports dry run and is all-or-nothing")
    void bulk() throws Exception {
        long a = product(TENANT_A, "A", 10, null), b = product(TENANT_A, "B", 10, null);
        send(post(BASE + "/prices"), maker(TENANT_A), price(a, "100.00", null), 201);
        send(post(BASE + "/prices"), maker(TENANT_A), price(b, "10.00", null), 201);
        Map<String, Object> req = new HashMap<>(Map.of("productIds", List.of(a, b), "mode", "PERCENT", "value", 10, "currency", "USD", "dryRun", true));
        JsonNode preview = send(post(BASE + "/prices/bulk"), maker(TENANT_A), req, 200);
        assertEquals(0, new BigDecimal("110.00").compareTo(preview.get(0).get("newBasePrice").decimalValue()));
        assertEquals(0, new BigDecimal("100.00").compareTo(send(get(BASE + "/prices?productId=" + a), maker(TENANT_A), null, 200).get(0).get("basePrice").decimalValue()));
        req.put("dryRun", false); req.put("value", -100);
        send(post(BASE + "/prices/bulk"), maker(TENANT_A), req, 400); // product B would reach 0 -> whole batch rejected
        assertEquals(0, new BigDecimal("100.00").compareTo(send(get(BASE + "/prices?productId=" + a), maker(TENANT_A), null, 200).get(0).get("basePrice").decimalValue()));
    }

    @Test @DisplayName("CSV export neutralizes formulas and import is all-or-nothing")
    void csv() throws Exception {
        long p = product(TENANT_A, "Csv", 10, null);
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "9.99", null), 201);
        String csv = "productId,variantId,currency,basePrice,salePrice\n" + p + ",0,USD,11.50,\n";
        mvc.perform(post(BASE + "/prices/import").header(HttpHeaders.AUTHORIZATION, "Bearer " + maker(TENANT_A)).contentType("text/csv").content(csv))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));
        String broken = "productId,currency,basePrice\n" + p + ",USD,12.00\n" + p + ",USD,-3\n";
        mvc.perform(post(BASE + "/prices/import").header(HttpHeaders.AUTHORIZATION, "Bearer " + maker(TENANT_A)).contentType("text/csv").content(broken))
                .andExpect(status().isBadRequest());
        assertEquals(0, new BigDecimal("11.50").compareTo(send(get(BASE + "/prices?productId=" + p), maker(TENANT_A), null, 200).get(0).get("basePrice").decimalValue()));
        String exported = mvc.perform(get(BASE + "/prices/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + maker(TENANT_A))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(exported.startsWith("productId,variantId,storeId,currency,basePrice,salePrice,costPrice"));
        assertEquals("'=SUM(A1)", PriceService.cell("=SUM(A1)"));
        assertEquals("-5.00", PriceService.cell("-5.00"));
    }

    @Test @DisplayName("scheduled price: approval needed, self-approval blocked, cancel, and due prices are applied")
    void scheduledPrices() throws Exception {
        long p = product(TENANT_A, "Sched", 10, null);
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "20.00", null), 201);
        Map<String, Object> past = new HashMap<>(Map.of("productId", p, "currency", "USD", "basePrice", 18, "effectiveFrom", OffsetDateTime.now().minusHours(1).toString()));
        send(post(BASE + "/scheduled"), maker(TENANT_A), past, 400);
        Map<String, Object> body = new HashMap<>(past); body.put("effectiveFrom", OffsetDateTime.now().plusDays(1).toString());
        long id = send(post(BASE + "/scheduled"), maker(TENANT_A), body, 201).get("id").asLong();
        send(post(BASE + "/scheduled"), maker(TENANT_A), body, 409); // same instant
        send(post(BASE + "/scheduled/" + id + "/decision"), maker(TENANT_A), Map.of("approve", true), 403); // own request
        assertEquals("SCHEDULED", send(post(BASE + "/scheduled/" + id + "/decision"), checker(TENANT_A), Map.of("approve", true), 200).get("status").asText());

        long other = send(post(BASE + "/scheduled"), maker(TENANT_A), new HashMap<>(Map.of("productId", p, "currency", "USD", "basePrice", 15,
                "effectiveFrom", OffsetDateTime.now().plusDays(2).toString())), 201).get("id").asLong();
        assertEquals("CANCELLED", send(post(BASE + "/scheduled/" + other + "/cancel"), maker(TENANT_A), null, 200).get("status").asText());
        send(post(BASE + "/scheduled/" + other + "/cancel"), maker(TENANT_A), null, 409);

        // Make the approved row due, then run the scheduler pass.
        jdbc.update("UPDATE scheduled_prices SET effective_from = ? WHERE id = ?", OffsetDateTime.now().minusMinutes(1), id);
        assertEquals(1, priceService.applyDueNow());
        assertEquals(0, new BigDecimal("18.00").compareTo(send(get(BASE + "/prices?productId=" + p), maker(TENANT_A), null, 200).get(0).get("basePrice").decimalValue()));
        assertEquals("SCHEDULED", send(get(BASE + "/prices/history?productId=" + p), maker(TENANT_A), null, 200).get(0).get("changeSource").asText());
        assertEquals(0, priceService.applyDueNow()); // idempotent
    }

    @Test @DisplayName("per-price guard rails: minimum selling price, max discount, tax flag, effective window; dashboard reports real money")
    void priceGuardRailsAndDashboard() throws Exception {
        long p = product(TENANT_A, "Guarded", 100, null);
        Map<String, Object> body = price(p, "100.00", null);
        body.put("minSellingPrice", new BigDecimal("90.00")); body.put("maxDiscountPercent", new BigDecimal("20")); body.put("taxInclusive", true);
        JsonNode created = send(post(BASE + "/prices"), maker(TENANT_A), body, 201);
        assertTrue(created.get("taxInclusive").asBoolean());
        assertEquals(0, new BigDecimal("90.00").compareTo(created.get("minSellingPrice").decimalValue()));
        Map<String, Object> below = price(p, "100.00", "85.00");
        send(put(BASE + "/prices"), maker(TENANT_A), below, 400); // sale below the minimum selling price
        Map<String, Object> window = price(p, "100.00", null);
        window.put("effectiveFrom", OffsetDateTime.now().minusDays(1).toString()); window.put("effectiveTo", OffsetDateTime.now().minusDays(2).toString());
        send(put(BASE + "/prices"), maker(TENANT_A), window, 400);
        Map<String, Object> badMax = price(p, "100.00", null); badMax.put("maxDiscountPercent", 150);
        send(put(BASE + "/prices"), maker(TENANT_A), badMax, 400);

        // a 50% campaign is limited by the product minimum selling price (90 -> max 10 off), stricter than its own 20% cap
        send(put(BASE + "/settings"), maker(TENANT_A), Map.of("maxDiscountPercent", 60), 200);
        long c = liveCampaign(TENANT_A, campaign("Half off", "PERCENTAGE", "PRODUCT", p, percentReward("50")));
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "HALF"), 201);
        JsonNode q = internal("quote", TENANT_A, quoteBody(p, 1, "HALF", "c1", null), 200);
        assertEquals(0, new BigDecimal("10.00").compareTo(q.get("discount").decimalValue()));
        internal("redeem", TENANT_A, quoteBody(p, 1, "HALF", "c1", "DASH-1"), 200);

        JsonNode dash = send(get(BASE + "/dashboard"), maker(TENANT_A), null, 200);
        assertEquals(1, dash.get("activeCampaigns").asInt());
        assertEquals(0, dash.get("expiredCampaigns").asInt());
        assertEquals(0, dash.get("productsWithSalePrice").asInt());
        assertEquals(1, dash.get("promotionOrdersByCurrency").size());
        assertEquals(0, new BigDecimal("10.00").compareTo(dash.get("promotionOrdersByCurrency").get(0).get("discountTotal").decimalValue()));
        assertEquals(0, new BigDecimal("90.00").compareTo(dash.get("promotionOrdersByCurrency").get(0).get("revenue").decimalValue()));
        assertTrue(dash.get("conversionRate").isNull());
    }

    // ---- tenant isolation -------------------------------------------------------------------------------------

    @Test @DisplayName("tenant B cannot see or change tenant A prices, campaigns, coupons or audit")
    void tenantIsolation() throws Exception {
        long p = product(TENANT_A, "A-only", 10, null);
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "10.00", null), 201);
        long c = liveCampaign(TENANT_A, campaign("A sale", "PERCENTAGE", "PRODUCT", p, percentReward("10")));
        long coupon = send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "ASECRET"), 201).get(0).get("id").asLong();

        String b = maker(TENANT_B);
        assertEquals(0, send(get(BASE + "/prices"), b, null, 200).size());
        assertEquals(0, send(get(BASE + "/campaigns"), b, null, 200).size());
        assertEquals(0, send(get(BASE + "/coupons"), b, null, 200).size());
        assertEquals(0, send(get(BASE + "/audit"), b, null, 200).size());
        send(get(BASE + "/campaigns/" + c), b, null, 404);
        send(post(BASE + "/campaigns/" + c + "/cancel"), b, null, 404);
        send(post(BASE + "/coupons/" + coupon + "/revoke"), b, null, 404);
        send(post(BASE + "/prices"), b, price(p, "1.00", null), 404); // product belongs to A
        send(post(BASE + "/coupons"), b, Map.of("campaignId", c, "code", "BHIJACK"), 404);
        Map<String, Object> forged = campaign("steal", "PERCENTAGE", "PRODUCT", p, percentReward("10"));
        send(post(BASE + "/campaigns"), b, forged, 400); // product not in tenant B

        // internal quote for tenant B cannot see A's coupon or product
        internal("quote", TENANT_B, quoteBody(p, 1, "ASECRET", "cust", null), 404);
        assertEquals("ACTIVE", send(get(BASE + "/coupons?q=ASECRET"), maker(TENANT_A), null, 200).get(0).get("status").asText());
    }

    // ---- campaigns ------------------------------------------------------------------------------------------

    @Test @DisplayName("campaign validation: dates, percent, max discount, currency, type-specific rewards")
    void campaignValidation() throws Exception {
        long p = product(TENANT_A, "V", 10, null);
        Map<String, Object> base = campaign("ok", "PERCENTAGE", "PRODUCT", p, percentReward("10"));
        Map<String, Object> badDates = new HashMap<>(base); badDates.put("endsAt", OffsetDateTime.now().minusDays(2).toString());
        send(post(BASE + "/campaigns"), maker(TENANT_A), badDates, 400);
        Map<String, Object> inverted = new HashMap<>(base); inverted.put("startsAt", OffsetDateTime.now().plusDays(9).toString());
        send(post(BASE + "/campaigns"), maker(TENANT_A), inverted, 400);
        send(post(BASE + "/campaigns"), maker(TENANT_A), campaign("too much", "PERCENTAGE", "PRODUCT", p, percentReward("75")), 400); // > 50% tenant max
        send(post(BASE + "/campaigns"), maker(TENANT_A), campaign("zero", "PERCENTAGE", "PRODUCT", p, percentReward("0")), 400);
        Map<String, Object> cur = new HashMap<>(base); cur.put("currency", "QQQ");
        send(post(BASE + "/campaigns"), maker(TENANT_A), cur, 400);
        Map<String, Object> wrongReward = campaign("mismatch", "FIXED_AMOUNT", "PRODUCT", p, percentReward("10"));
        send(post(BASE + "/campaigns"), maker(TENANT_A), wrongReward, 400);
        Map<String, Object> noTargets = new HashMap<>(base); noTargets.put("rules", List.of());
        send(post(BASE + "/campaigns"), maker(TENANT_A), noTargets, 400);
        Map<String, Object> limit = new HashMap<>(base); limit.put("usageLimitTotal", 0);
        send(post(BASE + "/campaigns"), maker(TENANT_A), limit, 400);
        send(post(BASE + "/campaigns"), maker(TENANT_A), base, 201);
        send(post(BASE + "/campaigns"), token(TENANT_A, 4, List.of("promotion:read")), base, 403);
    }

    @Test @DisplayName("approval workflow: threshold routing, no self-approval, reject needs a reason, publish needs approval")
    void approvalWorkflow() throws Exception {
        long p = product(TENANT_A, "W", 100, null);
        long small = send(post(BASE + "/campaigns"), maker(TENANT_A), campaign("small", "PERCENTAGE", "PRODUCT", p, percentReward("10")), 201).get("id").asLong();
        assertEquals("APPROVED", send(post(BASE + "/campaigns/" + small + "/submit"), maker(TENANT_A), null, 200).get("status").asText()); // <= 20% threshold
        long big = send(post(BASE + "/campaigns"), maker(TENANT_A), campaign("big", "PERCENTAGE", "PRODUCT", p, percentReward("30")), 201).get("id").asLong();
        send(post(BASE + "/campaigns/" + big + "/publish"), maker(TENANT_A), null, 409); // draft
        assertEquals("PENDING_APPROVAL", send(post(BASE + "/campaigns/" + big + "/submit"), maker(TENANT_A), null, 200).get("status").asText());
        send(post(BASE + "/campaigns/" + big + "/decision"), maker(TENANT_A), Map.of("approve", true), 403);
        send(post(BASE + "/campaigns/" + big + "/decision"), checker(TENANT_A), Map.of("approve", false), 400);
        assertEquals("REJECTED", send(post(BASE + "/campaigns/" + big + "/decision"), checker(TENANT_A), Map.of("approve", false, "note", "too deep"), 200).get("status").asText());
        Map<String, Object> v2 = campaign("big v2", "PERCENTAGE", "PRODUCT", p, percentReward("25")); v2.put("priority", 200);
        send(put(BASE + "/campaigns/" + big), maker(TENANT_A), v2, 200);
        send(post(BASE + "/campaigns/" + big + "/submit"), maker(TENANT_A), null, 200);
        send(post(BASE + "/campaigns/" + big + "/decision"), checker(TENANT_A), Map.of("approve", true), 200);
        send(post(BASE + "/campaigns/" + big + "/publish"), token(TENANT_A, 1, List.of("promotion:read")), null, 403);
        send(post(BASE + "/campaigns/" + big + "/publish"), maker(TENANT_A), null, 200);
        send(put(BASE + "/campaigns/" + big), maker(TENANT_A), campaign("edit live", "PERCENTAGE", "PRODUCT", p, percentReward("5")), 409); // published is immutable
        assertTrue(send(get(BASE + "/approvals?status=APPROVED"), maker(TENANT_A), null, 200).size() >= 2);

        JsonNode audit = send(get(BASE + "/audit?entityType=CAMPAIGN&entityId=" + big), maker(TENANT_A), null, 200);
        assertTrue(audit.size() >= 6);
    }

    @Test @DisplayName("overlapping same-priority non-stackable campaigns are rejected at publish; calendar reports clashes; cancel frees coupons")
    void conflictsAndCalendar() throws Exception {
        long p = product(TENANT_A, "Cal", 100, null);
        long first = liveCampaign(TENANT_A, campaign("First", "PERCENTAGE", "PRODUCT", p, percentReward("10")));
        long second = send(post(BASE + "/campaigns"), maker(TENANT_A), campaign("Second", "PERCENTAGE", "PRODUCT", p, percentReward("10")), 201).get("id").asLong();
        send(post(BASE + "/campaigns/" + second + "/submit"), maker(TENANT_A), null, 200);
        assertEquals(1, send(get(BASE + "/campaigns/" + second + "/conflicts"), maker(TENANT_A), null, 200).size());
        send(post(BASE + "/campaigns/" + second + "/publish"), maker(TENANT_A), null, 409);
        JsonNode cal = send(get(BASE + "/calendar").param("from", OffsetDateTime.now().minusDays(1).toString())
                .param("to", OffsetDateTime.now().plusDays(30).toString()), maker(TENANT_A), null, 200);
        assertEquals(2, cal.size());
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", first, "code", "CALFIRST"), 201);
        send(post(BASE + "/campaigns/" + first + "/cancel"), maker(TENANT_A), Map.of("note", "stop"), 200);
        assertEquals("REVOKED", send(get(BASE + "/coupons?q=CALFIRST"), maker(TENANT_A), null, 200).get(0).get("status").asText());
        send(post(BASE + "/campaigns/" + second + "/publish"), maker(TENANT_A), null, 200); // clash gone
    }

    // ---- coupons & redemption ------------------------------------------------------------------------------

    @Test @DisplayName("bulk coupon generation respects limits and creates unique codes")
    void bulkCoupons() throws Exception {
        long p = product(TENANT_A, "Bulk", 100, null);
        long c = liveCampaign(TENANT_A, campaign("Bulk", "PERCENTAGE", "PRODUCT", p, percentReward("10")));
        JsonNode codes = send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "count", 50, "prefix", "sum", "usageLimit", 1), 201);
        assertEquals(50, codes.size());
        assertEquals(50, new HashSet<>(codes.findValuesAsText("code")).size());
        assertTrue(codes.get(0).get("code").asText().startsWith("SUM"));
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "count", 5000), 400);
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "x"), 400);
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "DUP1"), 201);
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "dup1"), 409);
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "PASTX", "expiresAt", OffsetDateTime.now().minusDays(1).toString()), 400);
        long id = send(get(BASE + "/coupons?q=DUP1"), maker(TENANT_A), null, 200).get(0).get("id").asLong();
        assertEquals("PAUSED", send(post(BASE + "/coupons/" + id + "/pause"), maker(TENANT_A), null, 200).get("status").asText());
        send(post(BASE + "/coupons/" + id + "/pause"), maker(TENANT_A), null, 409);
        assertEquals("ACTIVE", send(post(BASE + "/coupons/" + id + "/activate"), maker(TENANT_A), null, 200).get("status").asText());
        assertEquals("REVOKED", send(post(BASE + "/coupons/" + id + "/revoke"), maker(TENANT_A), null, 200).get("status").asText());
        send(post(BASE + "/coupons/" + id + "/activate"), maker(TENANT_A), null, 409);
    }

    @Test @DisplayName("end to end: create campaign + coupon, quote, redeem at checkout, idempotent replay, history, reversal")
    void endToEndCheckout() throws Exception {
        long p = product(TENANT_A, "Gadget", 100, "40.00");
        send(post(BASE + "/prices"), maker(TENANT_A), price(p, "100.00", null), 201);
        Map<String, Object> req = campaign("Spring", "PERCENTAGE", "PRODUCT", p, percentReward("10"));
        req.put("usageLimitPerCustomer", 1);
        long c = liveCampaign(TENANT_A, req);
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "SPRING10"), 201);

        // The quote ignores any client price and computes on the server.
        JsonNode quote = internal("quote", TENANT_A, quoteBody(p, 2, "spring10", "cust-1", null), 200);
        assertEquals(0, new BigDecimal("200.00").compareTo(quote.get("subtotal").decimalValue()));
        assertEquals(0, new BigDecimal("20.00").compareTo(quote.get("discount").decimalValue()));
        assertEquals(0, new BigDecimal("180.00").compareTo(quote.get("total").decimalValue()));
        assertTrue(quote.get("couponValid").asBoolean());

        JsonNode redeemed = internal("redeem", TENANT_A, quoteBody(p, 2, "SPRING10", "cust-1", "ORDER-1"), 200);
        assertEquals(0, new BigDecimal("180.00").compareTo(redeemed.get("total").decimalValue()));
        JsonNode replay = internal("redeem", TENANT_A, quoteBody(p, 2, "SPRING10", "cust-1", "ORDER-1"), 200);
        assertTrue(replay.get("idempotentReplay").asBoolean());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemptions", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT redeemed_count FROM coupon_codes WHERE code='SPRING10'", Integer.class));

        // Per-customer limit: second order by the same customer is refused; another customer is fine.
        internal("redeem", TENANT_A, quoteBody(p, 1, "SPRING10", "cust-1", "ORDER-2"), 409);
        assertFalse(internal("quote", TENANT_A, quoteBody(p, 1, "SPRING10", "cust-1", null), 200).get("couponValid").asBoolean());
        internal("redeem", TENANT_A, quoteBody(p, 1, "SPRING10", "cust-2", "ORDER-3"), 200);

        // A placed order is immutable: cancelling the campaign / changing prices does not alter the stored result.
        send(post(BASE + "/campaigns/" + c + "/cancel"), maker(TENANT_A), null, 200);
        JsonNode stored = internal("redeem", TENANT_A, quoteBody(p, 2, "SPRING10", "cust-1", "ORDER-1"), 200);
        assertEquals(0, new BigDecimal("180.00").compareTo(stored.get("total").decimalValue()));
        assertEquals(2, send(get(BASE + "/redemptions?campaignId=" + c), maker(TENANT_A), null, 200).size());
        JsonNode perf = send(get(BASE + "/performance"), maker(TENANT_A), null, 200);
        assertEquals(2, perf.get(0).get("redemptions").asInt());

        // After cancellation the coupon no longer works for new orders.
        assertFalse(internal("quote", TENANT_A, quoteBody(p, 1, "SPRING10", "cust-9", null), 200).get("couponValid").asBoolean());

        // Reversal releases usage exactly once.
        assertTrue(internal("reverse/ORDER-3", TENANT_A, null, 200).get("reversed").asBoolean());
        assertFalse(internal("reverse/ORDER-3", TENANT_A, null, 200).get("reversed").asBoolean());
        assertEquals(1, jdbc.queryForObject("SELECT redeemed_count FROM coupon_codes WHERE code='SPRING10'", Integer.class));
    }

    @Test @DisplayName("expired, future, paused, unknown and restricted coupons never discount")
    void invalidCoupons() throws Exception {
        long p = product(TENANT_A, "Inv", 100, null);
        long live = liveCampaign(TENANT_A, campaign("Live", "PERCENTAGE", "PRODUCT", p, percentReward("10")));
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", live, "code", "SHORT", "expiresAt", OffsetDateTime.now().plusMinutes(5).toString()), 201);
        jdbc.update("UPDATE coupon_codes SET expires_at = ? WHERE code = 'SHORT'", OffsetDateTime.now().minusMinutes(1)); // time passes
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", live, "code", "VIPONLY", "restrictedCustomerId", "vip"), 201);
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", live, "code", "PAUSEME"), 201);
        long pauseId = send(get(BASE + "/coupons?q=PAUSEME"), maker(TENANT_A), null, 200).get(0).get("id").asLong();
        send(post(BASE + "/coupons/" + pauseId + "/pause"), maker(TENANT_A), null, 200);

        for (String code : List.of("SHORT", "VIPONLY", "PAUSEME", "NOPE")) {
            JsonNode q = internal("quote", TENANT_A, quoteBody(p, 1, code, "regular", null), 200);
            assertFalse(q.get("couponValid").asBoolean(), code);
            assertEquals(0, BigDecimal.ZERO.compareTo(q.get("discount").decimalValue()), code);
            internal("redeem", TENANT_A, quoteBody(p, 1, code, "regular", "ORD-" + code), 409);
        }
        assertTrue(internal("quote", TENANT_A, quoteBody(p, 1, "VIPONLY", "vip", null), 200).get("couponValid").asBoolean());

        // Future campaign: coupon exists but promotion is not live yet.
        Map<String, Object> future = campaign("Future", "PERCENTAGE", "PRODUCT", p, percentReward("10"));
        future.put("startsAt", OffsetDateTime.now().plusDays(3).toString()); future.put("endsAt", OffsetDateTime.now().plusDays(6).toString());
        future.put("priority", 50);
        long f = liveCampaign(TENANT_A, future);
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", f, "code", "FUTURE1"), 201);
        JsonNode q = internal("quote", TENANT_A, quoteBody(p, 1, "FUTURE1", "regular", null), 200);
        assertFalse(q.get("couponValid").asBoolean());
    }

    @Test @DisplayName("concurrent redemption never exceeds the coupon usage limit")
    void concurrentRedemption() throws Exception {
        long p = product(TENANT_A, "Hot", 100, null);
        long c = liveCampaign(TENANT_A, campaign("Flash", "PERCENTAGE", "PRODUCT", p, percentReward("10")));
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "FLASH5", "usageLimit", 5), 201);

        int attempts = 25;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), refused = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                try {
                    go.await();
                    MvcResult r = mvc.perform(post("/internal/pricing/redeem").header("X-Service-Token", "test-service-token").header("X-Tenant-Id", TENANT_A.toString())
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(quoteBody(p, 1, "FLASH5", "buyer-" + n, "RACE-" + n)))).andReturn();
                    if (r.getResponse().getStatus() == 200) ok.incrementAndGet(); else if (r.getResponse().getStatus() == 409) refused.incrementAndGet();
                } catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
        go.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(5, ok.get(), "exactly the limit must succeed");
        assertEquals(attempts - 5, refused.get());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemptions", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT redeemed_count FROM coupon_codes WHERE code='FLASH5'", Integer.class));
    }

    @Test @DisplayName("concurrent redemption of the same order is idempotent")
    void concurrentSameOrder() throws Exception {
        long p = product(TENANT_A, "Same", 100, null);
        long c = liveCampaign(TENANT_A, campaign("Dup", "PERCENTAGE", "PRODUCT", p, percentReward("10")));
        send(post(BASE + "/coupons"), maker(TENANT_A), Map.of("campaignId", c, "code", "DUPORDER"), 201);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) results.add(pool.submit(() -> {
            go.await();
            return mvc.perform(post("/internal/pricing/redeem").header("X-Service-Token", "test-service-token").header("X-Tenant-Id", TENANT_A.toString())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(quoteBody(p, 1, "DUPORDER", "same", "ONE-ORDER")))).andReturn().getResponse().getStatus();
        }));
        go.countDown();
        for (Future<Integer> f : results) assertEquals(200, f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemptions", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT usage_count FROM promotion_campaigns WHERE id=" + c, Integer.class));
    }

    @Test @DisplayName("automatic multi-promotion checkout: buy X get Y + percentage with flagged stacking; total computed server side")
    void automaticPromotions() throws Exception {
        long a = product(TENANT_A, "A", 50, null), b = product(TENANT_A, "B", 50, null);
        send(post(BASE + "/prices"), maker(TENANT_A), price(a, "50.00", null), 201);
        send(post(BASE + "/prices"), maker(TENANT_A), price(b, "50.00", null), 201);
        send(put(BASE + "/settings"), maker(TENANT_A), Map.of("maxDiscountPercent", 100), 200);
        Map<String, Object> bxgy = new HashMap<>(campaign("B2G1", "BUY_X_GET_Y", "PRODUCT", a, Map.of("type", "BUY_X_GET_Y", "buyQuantity", 2, "getQuantity", 1)));
        bxgy.put("automatic", true); bxgy.put("priority", 200); bxgy.put("stackable", true);
        bxgy.put("rules", List.of(Map.of("targetType", "PRODUCT", "targetId", a)));
        liveCampaign(TENANT_A, bxgy);
        Map<String, Object> pct = new HashMap<>(campaign("StoreWide5", "PERCENTAGE", "STORE_WIDE", a, percentReward("5")));
        pct.put("automatic", true); pct.put("priority", 100); pct.put("stackable", true); pct.put("rules", List.of());
        liveCampaign(TENANT_A, pct);

        Map<String, Object> body = new HashMap<>(Map.of("currency", "USD", "customer", Map.of("id", "x"),
                "lines", List.of(Map.of("productId", a, "quantity", 3), Map.of("productId", b, "quantity", 1))));
        JsonNode q = internal("quote", TENANT_A, body, 200);
        assertEquals(0, new BigDecimal("200.00").compareTo(q.get("subtotal").decimalValue()));
        assertEquals(2, q.get("applied").size());
        // 50 free, then 5% of the remaining 150 = 7.50
        assertEquals(0, new BigDecimal("57.50").compareTo(q.get("discount").decimalValue()));
        assertEquals(0, new BigDecimal("142.50").compareTo(q.get("total").decimalValue()));

        // Tenant switches stacking off -> only the highest priority applies.
        send(put(BASE + "/settings"), maker(TENANT_A), Map.of("stackingPolicy", "NO_STACKING"), 200);
        assertEquals(1, internal("quote", TENANT_A, body, 200).get("applied").size());
    }

    // ---- settings & internal API ---------------------------------------------------------------------------

    @Test @DisplayName("settings are validated data: bad values are rejected, good values change behaviour")
    void settings() throws Exception {
        for (Map<String, Object> bad : List.of(Map.<String, Object>of("maxDiscountPercent", 0), Map.<String, Object>of("maxDiscountPercent", 101),
                Map.<String, Object>of("minMarginPercent", 100), Map.<String, Object>of("stackingPolicy", "1; DROP TABLE product"),
                Map.<String, Object>of("supportedTypes", List.of("EXEC_SCRIPT")), Map.<String, Object>of("supportedCurrencies", List.of("USD", "NOPE")),
                Map.<String, Object>of("defaultCurrency", "JPY"), Map.<String, Object>of("couponBulkMax", 0), Map.<String, Object>of("defaultCampaignDays", 999))) {
            send(put(BASE + "/settings"), maker(TENANT_A), bad, 400);
        }
        send(put(BASE + "/settings"), token(TENANT_A, 3, List.of("pricing:read")), Map.of("maxDiscountPercent", 10), 403);
        JsonNode updated = send(put(BASE + "/settings"), maker(TENANT_A), Map.of("maxDiscountPercent", 10, "supportedTypes", List.of("PERCENTAGE")), 200);
        assertEquals(10, updated.get("maxDiscountPercent").decimalValue().intValue());
        long p = product(TENANT_A, "S", 10, null);
        send(post(BASE + "/campaigns"), maker(TENANT_A), campaign("pct 20", "PERCENTAGE", "PRODUCT", p, percentReward("20")), 400);
        send(post(BASE + "/campaigns"), maker(TENANT_A), campaign("fixed", "FIXED_AMOUNT", "PRODUCT", p, Map.of("type", "AMOUNT_OFF", "amount", 1)), 400);
        assertEquals(50, send(get(BASE + "/settings"), maker(TENANT_B), null, 200).get("maxDiscountPercent").decimalValue().intValue()); // B unaffected
    }

    @Test @DisplayName("internal pricing API requires the service token")
    void internalAuth() throws Exception {
        mvc.perform(post("/internal/pricing/quote").header("X-Tenant-Id", TENANT_A.toString()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/pricing/quote").header("X-Service-Token", "wrong").header("X-Tenant-Id", TENANT_A.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/pricing/quote").header("X-Service-Token", "test-service-token").header("X-Tenant-Id", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        long p = product(TENANT_A, "Q", 10, null);
        Map<String, Object> bad = quoteBody(p, 0, null, "c", null);
        internal("quote", TENANT_A, bad, 400);
        bad.put("currency", "BAD");
        internal("quote", TENANT_A, bad, 400);
    }
}

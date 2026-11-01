package com.ecommerce.product.storefront;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.security.CatalogAuthorizationClient;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real V7 + V8 migration text on H2 and exercises Storefront Management over HTTP, including the anonymous public API. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"storefront.scheduler.delay-ms=3600000", "pricing.scheduler.delay-ms=3600000", "storefront.base-domain=shops.test"})
class StorefrontApiIntegrationTest {
    private static final UUID A = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");
    private static final List<String> ALL = List.of("storefront:read", "storefront:manage", "storefront:edit", "storefront:media", "storefront:approve",
            "storefront:publish", "storefront:domain", "storefront:audit");
    private static final String ADMIN = "/api/products/admin/storefront";
    private static final String PUB = "/api/products/public/storefront";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProductRepository products;
    @Autowired private StorefrontService service;
    @MockBean private CatalogAuthorizationClient grants;
    @MockBean private DomainService.DnsVerifier dns;
    @MockBean private DomainService.TlsProbe tls;
    @Value("${jwt.secret}") private String secret;

    @BeforeEach
    void reset() {
        for (String s : List.of("V7__pricing_and_promotions.sql", "V8__storefront_content_studio.sql")) new ResourceDatabasePopulator(new ClassPathResource("db/migration/" + s)).execute(dataSource);
        wipe();
        doNothing().when(grants).requireCurrentGrant(anyString());
    }

    @AfterEach
    void cleanUp() { wipe(); }

    private void wipe() {
        for (String t : List.of("storefront_audit_logs", "storefront_theme_presets", "storefront_approval_history", "storefront_banners", "storefront_media", "storefront_domains",
                "storefront_versions", "storefront_documents", "storefronts", "promotion_campaigns")) jdbc.update("DELETE FROM " + t);
        products.deleteAll();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private String token(UUID tenant, long user, List<String> perms) {
        return Jwts.builder().setSubject("u" + user + "@" + tenant).setId(UUID.randomUUID().toString()).setIssuer("universal-commerce").setAudience("commerce-api")
                .setExpiration(new Date(System.currentTimeMillis() + 600_000)).claim("id", user).claim("tenant_id", tenant.toString()).claim("tenant_role", "STOREFRONT_MANAGER")
                .claim("application_code", "ECOMMERCE_ADMIN").claim("permissions", perms).signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
    }
    private String editor(UUID t) { return token(t, 1, ALL); }
    private String reviewer(UUID t) { return token(t, 2, ALL); }

    private JsonNode call(MockHttpServletRequestBuilder b, String token, Object body, int status) throws Exception {
        if (token != null) b = b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) b = b.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        MvcResult r = mvc.perform(b).andExpect(status().is(status)).andReturn();
        String c = r.getResponse().getContentAsString();
        return c.isBlank() || !(c.startsWith("{") || c.startsWith("[")) ? null : json.readTree(c);
    }

    private JsonNode pub(String path, int status) throws Exception { return call(get(PUB + path), null, null, status); }

    private long storefront(UUID tenant, String slug) throws Exception {
        return call(post(ADMIN), editor(tenant), Map.of("name", slug + " store", "slug", slug, "storeId", "store-" + slug), 201).get("id").asLong();
    }

    private long doc(UUID tenant, long sf, String type) throws Exception {
        for (JsonNode d : call(get(ADMIN + "/" + sf + "/documents"), editor(tenant), null, 200)) if (d.get("docType").asText().equals(type) && d.get("slug").asText().isEmpty()) return d.get("id").asLong();
        throw new AssertionError("no " + type);
    }

    private long versionId(UUID tenant, long doc, int no) throws Exception {
        for (JsonNode v : call(get(ADMIN + "/documents/" + doc + "/versions"), editor(tenant), null, 200)) if (v.get("versionNo").asInt() == no) return v.get("id").asLong();
        throw new AssertionError("no version " + no);
    }

    private Map<String, Object> home(String heading) {
        return Map.of("sections", List.of(Map.of("id", "hero1", "type", "HERO_BANNER", "props", Map.of("heading", heading, "buttonLabel", "Shop", "buttonUrl", "/sale"))));
    }

    private void saveSubmitApprove(UUID t, long doc, Object content) throws Exception {
        long vid = call(put(ADMIN + "/documents/" + doc + "/draft"), editor(t), Map.of("content", content, "note", "edit"), 200).get("id").asLong();
        call(post(ADMIN + "/documents/" + doc + "/versions/" + vid + "/submit"), editor(t), null, 200);
        call(post(ADMIN + "/documents/" + doc + "/versions/" + vid + "/decision"), reviewer(t), Map.of("approve", true), 200);
    }

    private long publishNow(UUID t, long doc, Object content) throws Exception {
        saveSubmitApprove(t, doc, content);
        JsonNode versions = call(get(ADMIN + "/documents/" + doc + "/versions"), editor(t), null, 200);
        long vid = versions.get(0).get("id").asLong();
        call(post(ADMIN + "/documents/" + doc + "/versions/" + vid + "/publish"), editor(t), Map.of(), 200);
        return vid;
    }

    private long product(UUID tenant, String name, int stock, boolean active) {
        return products.save(Product.builder().tenantId(tenant).name(name).price(25.0).stockQuantity(stock).active(active).build()).getId();
    }

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private JsonNode upload(UUID tenant, long sf, String kind, byte[] bytes, String name, String alt, int status) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "image/png", bytes);
        MvcResult r = mvc.perform(multipart(ADMIN + "/" + sf + "/media").file(file).param("kind", kind).param("altText", alt).header(HttpHeaders.AUTHORIZATION, "Bearer " + editor(tenant)))
                .andExpect(status().is(status)).andReturn();
        String c = r.getResponse().getContentAsString();
        return c.startsWith("{") ? json.readTree(c) : null;
    }

    // ---- tests -----------------------------------------------------------------------------------------------------

    @Test @DisplayName("authentication and permissions are enforced by the backend")
    void permissions() throws Exception {
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        long sf = storefront(A, "perm");
        String readOnly = token(A, 5, List.of("storefront:read"));
        call(get(ADMIN), readOnly, null, 200);
        call(post(ADMIN), readOnly, Map.of("name", "x", "slug", "x1"), 403);
        long homeDoc = doc(A, sf, "HOMEPAGE");
        call(put(ADMIN + "/documents/" + homeDoc + "/draft"), readOnly, Map.of("content", home("Hi")), 403);
        call(get(ADMIN + "/" + sf + "/audit"), readOnly, null, 403);
        String editOnly = token(A, 6, List.of("storefront:read", "storefront:edit"));
        long vid = call(put(ADMIN + "/documents/" + homeDoc + "/draft"), editOnly, Map.of("content", home("Hi")), 200).get("id").asLong();
        call(post(ADMIN + "/documents/" + homeDoc + "/versions/" + vid + "/submit"), editOnly, null, 200);
        call(post(ADMIN + "/documents/" + homeDoc + "/versions/" + vid + "/decision"), editOnly, Map.of("approve", true), 403);
        call(post(ADMIN + "/documents/" + homeDoc + "/versions/" + vid + "/publish"), editOnly, Map.of(), 403);
        call(post(ADMIN + "/" + sf + "/domains"), editOnly, Map.of("hostname", "shop.example.com"), 403);
        assertEquals(403, mvc.perform(multipart(ADMIN + "/" + sf + "/media").file(new MockMultipartFile("file", "a.png", "image/png", png(100, 100))).param("kind", "GENERAL").param("altText", "a")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + editOnly)).andReturn().getResponse().getStatus());
    }

    @Test @DisplayName("storefront creation validates slug and enforces global slug uniqueness without leaking the other tenant")
    void creation() throws Exception {
        call(post(ADMIN), editor(A), Map.of("name", "Bad", "slug", "Bad Slug!"), 400);
        call(post(ADMIN), editor(A), Map.of("name", "Bad", "slug", "-bad"), 400);
        call(post(ADMIN), editor(A), Map.of("name", "Reserved", "slug", "admin"), 400);
        call(post(ADMIN), editor(A), Map.of("name", "Bad", "slug", "ok-slug", "storefrontType", "MARKETPLACE"), 400);
        storefront(A, "acme");
        call(post(ADMIN), editor(B), Map.of("name", "Other", "slug", "acme"), 409);
        call(post(ADMIN), editor(A), Map.of("name", "Same store", "slug", "acme2", "storeId", "store-acme"), 409);
        JsonNode created = call(post(ADMIN), editor(A), Map.of("name", "Diner", "slug", "diner", "storefrontType", "RESTAURANT", "storeId", "s2"), 201);
        assertEquals("RESTAURANT", created.get("storefrontType").asText());
        assertEquals(4, call(get(ADMIN + "/" + created.get("id").asLong() + "/documents"), editor(A), null, 200).size());
    }

    @Test @DisplayName("tenant B cannot read or change tenant A storefronts; publishing for A never alters B")
    void tenantIsolation() throws Exception {
        long sfA = storefront(A, "alpha"), sfB = storefront(B, "beta");
        long homeA = doc(A, sfA, "HOMEPAGE"), homeB = doc(B, sfB, "HOMEPAGE");
        long bBefore = publishNow(B, homeB, home("Beta welcome"));

        String b = editor(B);
        call(get(ADMIN + "/" + sfA), b, null, 404);
        call(get(ADMIN + "/" + sfA + "/documents"), b, null, 404);
        call(get(ADMIN + "/documents/" + homeA + "/versions"), b, null, 404);
        call(put(ADMIN + "/documents/" + homeA + "/draft"), b, Map.of("content", home("Hijack")), 404);
        call(post(ADMIN + "/documents/" + homeA + "/versions/1/publish"), b, Map.of(), 404);
        call(get(ADMIN + "/documents/" + homeA + "/preview"), b, null, 404);
        call(get(ADMIN + "/" + sfA + "/media"), b, null, 404);
        call(post(ADMIN + "/" + sfA + "/banners"), b, Map.of("name", "x", "desktopMediaId", 1, "altText", "a"), 404);
        call(post(ADMIN + "/" + sfA + "/domains"), b, Map.of("hostname", "steal.example.com"), 404);
        call(get(ADMIN + "/" + sfA + "/audit"), b, null, 404);
        assertEquals(1, call(get(ADMIN), b, null, 200).size());

        long aLive = publishNow(A, homeA, home("Alpha welcome"));
        assertEquals("Alpha welcome", pub("/alpha/home", 200).at("/content/sections/0/props/heading").asText());
        assertEquals("Beta welcome", pub("/beta/home", 200).at("/content/sections/0/props/heading").asText());
        assertEquals("PUBLISHED", call(get(ADMIN + "/documents/" + homeB + "/versions/" + bBefore), b, null, 200).get("status").asText());
        assertNotEquals(aLive, bBefore);
        // B's token cannot even address A's version ids
        call(get(ADMIN + "/documents/" + homeB + "/versions/" + aLive), b, null, 404);
    }

    @Test @DisplayName("workflow: review rules, immutable published snapshot, drafts invisible to the public, rollback and compare")
    void publishingWorkflow() throws Exception {
        long sf = storefront(A, "flow");
        long homeId = doc(A, sf, "HOMEPAGE");
        pub("/flow/home", 404); // nothing published yet
        long v1 = call(put(ADMIN + "/documents/" + homeId + "/draft"), editor(A), Map.of("content", home("First")), 200).get("id").asLong();
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/publish"), editor(A), Map.of(), 409); // not approved
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/submit"), editor(A), null, 200);
        call(put(ADMIN + "/documents/" + homeId + "/draft"), editor(A), Map.of("content", home("Sneaky edit")), 409); // in review is locked
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/decision"), editor(A), Map.of("approve", true), 403); // own submission
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/decision"), reviewer(A), Map.of("approve", false), 400); // reason needed
        assertEquals("DRAFT", call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/decision"), reviewer(A), Map.of("approve", false, "note", "typo"), 200).get("status").asText());
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/submit"), editor(A), null, 200);
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/decision"), reviewer(A), Map.of("approve", true), 200);
        assertEquals("PUBLISHED", call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/publish"), editor(A), Map.of(), 200).get("status").asText());
        assertEquals("First", pub("/flow/home", 200).at("/content/sections/0/props/heading").asText());

        // Editing after publishing starts v2 as a draft; the live site is untouched and v1 content is immutable.
        String hashBefore = jdbc.queryForObject("SELECT content_hash FROM storefront_versions WHERE id=?", String.class, v1);
        JsonNode v2 = call(put(ADMIN + "/documents/" + homeId + "/draft"), editor(A), Map.of("content", home("Second")), 200);
        assertEquals(2, v2.get("versionNo").asInt());
        assertEquals("DRAFT", v2.get("status").asText());
        assertEquals("First", pub("/flow/home", 200).at("/content/sections/0/props/heading").asText());
        assertEquals(hashBefore, jdbc.queryForObject("SELECT content_hash FROM storefront_versions WHERE id=?", String.class, v1));
        assertEquals("Second", call(get(ADMIN + "/documents/" + homeId + "/preview"), editor(A), null, 200).at("/content/sections/0/props/heading").asText());
        call(get(ADMIN + "/documents/" + homeId + "/preview"), null, null, 401);

        long v2id = v2.get("id").asLong();
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v2id + "/submit"), editor(A), null, 200);
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v2id + "/decision"), reviewer(A), Map.of("approve", true), 200);
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v2id + "/publish"), editor(A), Map.of(), 200);
        assertEquals("Second", pub("/flow/home", 200).at("/content/sections/0/props/heading").asText());
        assertEquals("ARCHIVED", call(get(ADMIN + "/documents/" + homeId + "/versions/" + v1), editor(A), null, 200).get("status").asText());

        JsonNode diff = call(get(ADMIN + "/documents/" + homeId + "/compare?from=" + v1 + "&to=" + v2id), editor(A), null, 200);
        assertEquals(1, diff.get("changes").size());
        assertEquals("CHANGED", diff.get("changes").get(0).get("change").asText());

        // Rollback: v1 content becomes a new live version v3; v2 stays in history; an open draft would be preserved.
        JsonNode v3 = call(post(ADMIN + "/documents/" + homeId + "/versions/" + v1 + "/rollback"), editor(A), Map.of("note", "bad copy"), 200);
        assertEquals(3, v3.get("versionNo").asInt());
        assertEquals("PUBLISHED", v3.get("status").asText());
        assertEquals("First", pub("/flow/home", 200).at("/content/sections/0/props/heading").asText());
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + v3.get("id").asLong() + "/rollback"), editor(A), Map.of(), 409); // live version cannot be a rollback target
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM storefront_versions WHERE document_id=" + homeId + " AND status='PUBLISHED'", Integer.class));

        List<String> actions = new ArrayList<>();
        call(get(ADMIN + "/" + sf + "/audit"), editor(A), null, 200).forEach(n -> actions.add(n.get("action").asText()));
        assertTrue(actions.containsAll(List.of("SUBMIT", "APPROVE", "REJECT", "PUBLISH", "ROLLBACK", "SAVE_DRAFT", "CREATE")));
        assertTrue(call(get(ADMIN + "/documents/" + homeId + "/versions/" + v1), editor(A), null, 200).get("approvals").size() >= 3);
    }

    @Test @DisplayName("scheduled publishing goes live only when due and can be cancelled")
    void scheduling() throws Exception {
        long sf = storefront(A, "sched");
        long homeId = doc(A, sf, "HOMEPAGE");
        saveSubmitApprove(A, homeId, home("Scheduled"));
        long vid = versionId(A, homeId, 1);
        JsonNode scheduled = call(post(ADMIN + "/documents/" + homeId + "/versions/" + vid + "/publish"), editor(A), Map.of("at", OffsetDateTime.now().plusDays(2).toString()), 200);
        assertEquals("APPROVED", scheduled.get("status").asText());
        assertNotNull(scheduled.get("scheduledPublishAt"));
        pub("/sched/home", 404);
        assertEquals(0, service.publishDueNow());
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + vid + "/cancel-schedule"), editor(A), null, 200);
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + vid + "/cancel-schedule"), editor(A), null, 409);
        call(post(ADMIN + "/documents/" + homeId + "/versions/" + vid + "/publish"), editor(A), Map.of("at", OffsetDateTime.now().plusDays(1).toString()), 200);
        jdbc.update("UPDATE storefront_versions SET scheduled_publish_at=? WHERE id=?", OffsetDateTime.now().minusMinutes(1), vid); // time passes
        assertEquals(1, service.publishDueNow());
        assertEquals("Scheduled", pub("/sched/home", 200).at("/content/sections/0/props/heading").asText());
        assertEquals(0, service.publishDueNow());
    }

    @Test @DisplayName("content validation rejects scripts, markup, unsafe links and unknown structures")
    void contentValidation() throws Exception {
        long sf = storefront(A, "valid");
        long homeId = doc(A, sf, "HOMEPAGE");
        String draft = ADMIN + "/documents/" + homeId + "/draft";
        java.util.function.Function<Map<String, Object>, Object> section = props -> Map.of("sections", List.of(Map.of("id", "s1", "type", "HERO_BANNER", "props", props)));
        call(put(draft), editor(A), Map.of("content", section.apply(Map.of("heading", "<script>alert(1)</script>"))), 400);
        call(put(draft), editor(A), Map.of("content", section.apply(Map.of("heading", "<img src=x onerror=alert(1)>"))), 400);
        call(put(draft), editor(A), Map.of("content", section.apply(Map.of("heading", "Hi", "buttonUrl", "javascript:alert(1)"))), 400);
        call(put(draft), editor(A), Map.of("content", section.apply(Map.of("heading", "Hi", "buttonUrl", "//evil.example"))), 400);
        call(put(draft), editor(A), Map.of("content", section.apply(Map.of("heading", "Hi", "buttonUrl", "http://insecure.example"))), 400);
        call(put(draft), editor(A), Map.of("content", section.apply(Map.of("heading", "Hi", "buttonUrl", "data:text/html;base64,AAAA"))), 400);
        call(put(draft), editor(A), Map.of("content", section.apply(Map.of("heading", "Hi", "buttonUrl", "https://good.example/sale"))), 200);
        call(put(draft), editor(A), Map.of("content", Map.of("sections", List.of(Map.of("id", "s1", "type", "SCRIPT_BLOCK", "props", Map.of())))), 400);
        call(put(draft), editor(A), Map.of("content", Map.of("sections", List.of(Map.of("id", "s1", "type", "NEWSLETTER"), Map.of("id", "s1", "type", "NEWSLETTER")))), 400);
        call(put(draft), editor(A), Map.of("content", section.apply(Map.of("desktopMediaId", 9999))), 400); // media that does not exist here
        call(put(draft), editor(A), Map.of("content", Map.of("sections", List.of(Map.of("id", "s1", "type", "BEST_SELLERS", "props", Map.of())))), 400);
        long foreign = product(B, "Not yours", 5, true);
        call(put(draft), editor(A), Map.of("content", Map.of("sections", List.of(Map.of("id", "s1", "type", "FEATURED_PRODUCTS", "props", Map.of("productIds", List.of(foreign)))))), 400);
        call(put(draft), editor(A), Map.of("content", Map.of("sections", List.of(Map.of("id", "s1", "type", "COUNTDOWN", "props", Map.of("endsAt", "tomorrow"))))), 400);
        call(put(draft), editor(A), Map.of("content", Map.of("sections", "not-an-array")), 400);
        call(put(draft), editor(A), Map.of("content", Map.of("seo", Map.of("title", "x".repeat(71)))), 400);
        call(put(draft), editor(A), Map.of("content", Map.of("seo", Map.of("canonicalUrl", "http://not-https.example"))), 400);
        call(put(draft), editor(A), Map.of("content", Map.of("seo", Map.of("robots", "EVERYTHING"))), 400);
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) many.add(Map.of("id", "s" + i, "type", "NEWSLETTER"));
        call(put(draft), editor(A), Map.of("content", Map.of("sections", many)), 400);
        // Unknown extra fields are dropped, not stored.
        JsonNode saved = call(put(draft), editor(A), Map.of("content", Map.of("sections", List.of(Map.of("id", "ok", "type", "NEWSLETTER", "props", Map.of("heading", "Join", "evil", "x"), "onclick", "x")), "script", "x")), 200);
        assertFalse(saved.at("/content/sections/0/props").has("evil"));
        assertFalse(saved.get("content").has("script"));
        assertEquals("ok", saved.at("/content/sections/0/id").asText());
    }

    @Test @DisplayName("theme, navigation and settings are validated typed configuration")
    void themeNavigationSettings() throws Exception {
        long sf = storefront(A, "cfg");
        long theme = doc(A, sf, "THEME"), nav = doc(A, sf, "NAVIGATION"), settings = doc(A, sf, "SETTINGS");
        String t = ADMIN + "/documents/" + theme + "/draft";
        call(put(t), editor(A), Map.of("content", Map.of("colors", Map.of("primary", "red"))), 400);
        call(put(t), editor(A), Map.of("content", Map.of("colors", Map.of("primary", "#12345"))), 400);
        call(put(t), editor(A), Map.of("content", Map.of("headingFont", "Comic Sans")), 400);
        call(put(t), editor(A), Map.of("content", Map.of("buttonStyle", "TRIANGLE")), 400);
        call(put(t), editor(A), Map.of("content", Map.of("colors", Map.of("primary", "#1D4ED8"), "headingFont", "Poppins", "colorMode", "DARK")), 200);

        String n = ADMIN + "/documents/" + nav + "/draft";
        call(put(n), editor(A), Map.of("content", Map.of("header", List.of(Map.of("label", "Bad", "url", "javascript:alert(1)")))), 400);
        call(put(n), editor(A), Map.of("content", Map.of("header", List.of(Map.of("label", "NoTarget")))), 400);
        Map<String, Object> deep = Map.of("label", "L1", "children", List.of(Map.of("label", "L2", "children", List.of(Map.of("label", "L3", "children", List.of(Map.of("label", "L4", "url", "/x")))))));
        call(put(n), editor(A), Map.of("content", Map.of("header", List.of(deep))), 400);
        call(put(n), editor(A), Map.of("content", Map.of("header", List.of(Map.of("label", "Shop", "url", "/shop", "children", List.of(Map.of("label", "Sale", "url", "/sale", "promotional", true))),
                Map.of("label", "Partner", "url", "https://partner.example/")), "footer", List.of(Map.of("label", "Privacy", "url", "/privacy")))), 200);

        String s = ADMIN + "/documents/" + settings + "/draft";
        call(put(s), editor(A), Map.of("content", Map.of("restaurant", Map.of("openingHours", Map.of("MON", Map.of("open", "25:00", "close", "22:00"))))), 400);
        call(put(s), editor(A), Map.of("content", Map.of("restaurant", Map.of("openingHours", Map.of("MON", Map.of("open", "22:00", "close", "09:00"))))), 400);
        call(put(s), editor(A), Map.of("content", Map.of("restaurant", Map.of("openingHours", Map.of("FUNDAY", Map.of("open", "09:00", "close", "10:00"))))), 400);
        call(put(s), editor(A), Map.of("content", Map.of("display", Map.of("badges", List.of("HOT_DEAL")))), 400);
        call(put(s), editor(A), Map.of("content", Map.of("contact", Map.of("email", "not-an-email"))), 400);
        call(put(s), editor(A), Map.of("content", Map.of("display", Map.of("badges", List.of("NEW", "SALE"), "productsPerPage", 12),
                "restaurant", Map.of("orderingAvailable", true, "openingHours", Map.of("MON", Map.of("open", "09:00", "close", "22:00"), "SUN", Map.of("closed", true))))), 200);

        // presets
        assertEquals(3, call(get(ADMIN + "/theme-presets"), editor(A), null, 200).size());
        call(post(ADMIN + "/" + sf + "/theme-presets"), editor(A), Map.of("name", "Brand", "theme", Map.of("colors", Map.of("primary", "#112233"))), 201);
        call(post(ADMIN + "/" + sf + "/theme-presets"), editor(A), Map.of("name", "Brand", "theme", Map.of()), 409);
        assertEquals(4, call(get(ADMIN + "/theme-presets"), editor(A), null, 200).size());
        assertEquals(3, call(get(ADMIN + "/theme-presets"), editor(B), null, 200).size()); // not shared across tenants
    }

    @Test @DisplayName("landing pages: unique slugs per storefront, publish/unpublish, drafts never public, noindex excluded from sitemap")
    void landingPages() throws Exception {
        long sf1 = storefront(A, "land1"), sf2 = storefront(A, "land2");
        JsonNode page = call(post(ADMIN + "/" + sf1 + "/pages"), editor(A), Map.of("slug", "summer-sale", "title", "Summer Sale"), 201);
        call(post(ADMIN + "/" + sf1 + "/pages"), editor(A), Map.of("slug", "summer-sale", "title", "Dup"), 409);
        call(post(ADMIN + "/" + sf2 + "/pages"), editor(A), Map.of("slug", "summer-sale", "title", "Other store, same slug"), 201);
        call(post(ADMIN + "/" + sf1 + "/pages"), editor(A), Map.of("slug", "Bad Slug", "title", "x"), 400);
        call(post(ADMIN + "/" + sf1 + "/pages"), editor(A), Map.of("slug", "checkout", "title", "x"), 400);
        pub("/land1/pages/summer-sale", 404); // draft only
        long id = page.get("id").asLong();
        long vid = publishNow(A, id, Map.of("template", "PROMOTION", "sections", List.of(Map.of("id", "c", "type", "COUNTDOWN", "props", Map.of("endsAt", "2030-01-01T00:00:00Z"))),
                "seo", Map.of("title", "Summer Sale", "description", "Big savings", "canonicalUrl", "https://shop.example.com/p/summer-sale", "robots", "INDEX_FOLLOW")));
        assertTrue(vid > 0);
        assertEquals("Summer Sale", pub("/land1/pages/summer-sale", 200).get("title").asText());
        pub("/land2/pages/summer-sale", 404);
        long hidden = call(post(ADMIN + "/" + sf1 + "/pages"), editor(A), Map.of("slug", "private-offer", "title", "Private"), 201).get("id").asLong();
        publishNow(A, hidden, Map.of("seo", Map.of("robots", "NOINDEX_FOLLOW")));
        mvc.perform(get(PUB + "/land1/sitemap.xml")).andExpect(status().isOk());
        String sitemap = mvc.perform(get(PUB + "/land1/sitemap.xml")).andReturn().getResponse().getContentAsString();
        assertTrue(sitemap.contains("/p/summer-sale"));
        assertFalse(sitemap.contains("private-offer"));
        String robots = mvc.perform(get(PUB + "/land1/robots.txt")).andReturn().getResponse().getContentAsString();
        assertTrue(robots.contains("Sitemap: https://land1.shops.test/sitemap.xml"));

        call(post(ADMIN + "/documents/" + id + "/unpublish"), editor(A), null, 200);
        pub("/land1/pages/summer-sale", 404);
        call(post(ADMIN + "/documents/" + id + "/unpublish"), editor(A), null, 409);
        call(post(ADMIN + "/documents/" + doc(A, sf1, "HOMEPAGE") + "/unpublish"), editor(A), null, 409); // homepage cannot go dark
    }

    @Test @DisplayName("public home resolves catalog data at read time and respects availability, status and tenant")
    void retailResolution() throws Exception {
        long sf = storefront(A, "retail");
        long inStock = product(A, "Sneaker", 50, true), low = product(A, "Boot", 3, true), out = product(A, "Sandal", 0, true), inactive = product(A, "Archived", 9, false);
        long homeId = doc(A, sf, "HOMEPAGE");
        call(put(ADMIN + "/documents/" + homeId + "/draft"), editor(A), Map.of("content", Map.of("sections", List.of(Map.of("id", "f", "type", "FEATURED_PRODUCTS", "props", Map.of("productIds", List.of(low, inStock, out, inactive)))))), 200);
        publishNow(A, homeId, Map.of("sections", List.of(
                Map.of("id", "f", "type", "FEATURED_PRODUCTS", "props", Map.of("heading", "Picks", "productIds", List.of(low, inStock, out, inactive))),
                Map.of("id", "n", "type", "NEW_ARRIVALS", "props", Map.of("limit", 2)))));
        JsonNode home = pub("/retail/home", 200);
        JsonNode featured = home.at("/content/sections/0/props/products");
        assertEquals(3, featured.size()); // inactive product is not shown
        assertEquals("Boot", featured.get(0).get("name").asText()); // curated order kept
        assertEquals("LOW_STOCK", featured.get(0).get("availability").asText());
        assertEquals("IN_STOCK", featured.get(1).get("availability").asText());
        assertEquals("OUT_OF_STOCK", featured.get(2).get("availability").asText());
        assertEquals(2, home.at("/content/sections/1/props/products").size());

        // price/stock changes in the catalog show up without republishing (nothing is copied into storefront content)
        jdbc.update("UPDATE product SET stock_quantity=0 WHERE id=?", inStock);
        assertEquals("OUT_OF_STOCK", pub("/retail/home", 200).at("/content/sections/0/props/products/1/availability").asText());

        jdbc.update("UPDATE storefronts SET status='SUSPENDED' WHERE slug='retail'");
        pub("/retail/home", 404);
        pub("/retail", 404);
    }

    @Test @DisplayName("restaurant storefront publishes opening hours and ordering availability as structured data")
    void restaurant() throws Exception {
        long sf = call(post(ADMIN), editor(A), Map.of("name", "Spice", "slug", "spice", "storefrontType", "RESTAURANT", "storeId", "s-spice"), 201).get("id").asLong();
        publishNow(A, doc(A, sf, "SETTINGS"), Map.of("restaurant", Map.of("orderingAvailable", true, "showVegIndicator", true, "showPreparationTime", true,
                "openingHours", Map.of("MON", Map.of("open", "11:00", "close", "22:00"), "TUE", Map.of("closed", true))), "contact", Map.of("phone", "+1 555 0100")));
        JsonNode site = pub("/spice", 200);
        assertEquals("RESTAURANT", site.get("type").asText());
        assertTrue(site.at("/settings/restaurant/orderingAvailable").asBoolean());
        assertEquals("Restaurant", site.at("/structuredData/@type").asText());
        assertEquals(1, site.at("/structuredData/openingHoursSpecification").size());
        assertEquals("Monday", site.at("/structuredData/openingHoursSpecification/0/dayOfWeek").asText());
        assertTrue(site.get("theme").isNull()); // nothing published for theme yet -> absent, not a default
    }

    @Test @DisplayName("media: format, size, dimensions and ownership are validated; public delivery is hardened")
    void media() throws Exception {
        long sf = storefront(A, "media"), other = storefront(B, "media-b");
        assertNotNull(upload(A, sf, "BANNER_DESKTOP", png(1600, 400), "../../etc/passwd.png", "A banner", 201).get("publicId"));
        JsonNode saved = upload(A, sf, "LOGO", png(200, 80), "logo.png", "Logo", 201);
        assertEquals("logo.png", saved.get("fileName").asText());
        JsonNode traversal = call(get(ADMIN + "/" + sf + "/media?kind=BANNER_DESKTOP"), editor(A), null, 200);
        assertEquals("passwd.png", traversal.get(0).get("fileName").asText());
        upload(A, sf, "BANNER_DESKTOP", png(800, 400), "small.png", "Too small", 400);
        upload(A, sf, "BANNER_DESKTOP", png(5000, 400), "huge.png", "Too wide", 400);
        upload(A, sf, "FAVICON", png(64, 48), "fav.png", "Not square", 400);
        upload(A, sf, "FAVICON", png(64, 64), "fav.png", "Favicon", 201);
        upload(A, sf, "NOPE", png(100, 100), "x.png", "x", 400);
        upload(A, sf, "GENERAL", png(100, 100), "x.png", "<b>bold</b>", 400);
        upload(A, sf, "GENERAL", "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(), "x.svg", "svg", 415);
        upload(A, sf, "GENERAL", "GIF89a not really".getBytes(), "x.gif", "fake gif", 400);
        upload(A, sf, "GENERAL", "just text".getBytes(), "x.png", "text", 415);
        upload(A, sf, "GENERAL", new byte[0], "x.png", "empty", 400);
        byte[] big = new byte[6 * 1024 * 1024];
        big[0] = (byte) 0x89; big[1] = 'P'; big[2] = 'N'; big[3] = 'G';
        upload(A, sf, "GENERAL", big, "big.png", "big", 413);
        upload(B, sf, "GENERAL", png(100, 100), "x.png", "other tenant", 404);
        assertEquals(0, call(get(ADMIN + "/" + other + "/media"), editor(B), null, 200).size());

        String publicId = saved.get("publicId").asText();
        MvcResult r = mvc.perform(get(PUB + "/media/" + publicId)).andExpect(status().isOk()).andReturn();
        assertEquals("image/png", r.getResponse().getContentType());
        assertEquals("nosniff", r.getResponse().getHeader("X-Content-Type-Options"));
        mvc.perform(get(PUB + "/media/not-a-uuid")).andExpect(status().isNotFound());
        mvc.perform(get(PUB + "/media/" + UUID.randomUUID())).andExpect(status().isNotFound());

        // In-use media cannot be deleted; another tenant's media cannot be referenced.
        long logoId = saved.get("id").asLong();
        long themeDoc = doc(A, sf, "THEME");
        call(put(ADMIN + "/documents/" + themeDoc + "/draft"), editor(A), Map.of("content", Map.of("logoMediaId", logoId)), 200);
        mvc.perform(delete(ADMIN + "/media/" + logoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + editor(A))).andExpect(status().isConflict());
        mvc.perform(delete(ADMIN + "/media/" + logoId).header(HttpHeaders.AUTHORIZATION, "Bearer " + editor(B))).andExpect(status().isNotFound());
        long bMedia = upload(B, other, "LOGO", png(200, 80), "b.png", "B logo", 201).get("id").asLong();
        call(put(ADMIN + "/documents/" + themeDoc + "/draft"), editor(A), Map.of("content", Map.of("logoMediaId", bMedia)), 400);
        // published theme resolves media ids into urls
        publishNow(A, themeDoc, Map.of("logoMediaId", logoId, "colors", Map.of("primary", "#112233")));
        JsonNode site = pub("/media", 200);
        assertTrue(site.at("/theme/logoImage/url").asText().endsWith(publicId));
        assertEquals("Logo", site.at("/theme/logoImage/alt").asText());
        assertFalse(site.get("theme").has("logoMediaId"));
    }

    @Test @DisplayName("banners: image kinds, safe links, campaign ownership, schedule windows, ordering and public visibility")
    void banners() throws Exception {
        long sf = storefront(A, "ban"), sfB = storefront(B, "ban-b");
        long desktop = upload(A, sf, "BANNER_DESKTOP", png(1600, 400), "d.png", "Desktop", 201).get("id").asLong();
        long mobile = upload(A, sf, "BANNER_MOBILE", png(800, 600), "m.png", "Mobile", 201).get("id").asLong();
        long logo = upload(A, sf, "LOGO", png(200, 80), "l.png", "Logo", 201).get("id").asLong();
        Map<String, Object> ok = new HashMap<>(Map.of("name", "Spring", "desktopMediaId", desktop, "mobileMediaId", mobile, "altText", "Spring sale", "destinationUrl", "/sale"));
        Map<String, Object> wrongKind = new HashMap<>(ok); wrongKind.put("desktopMediaId", logo);
        call(post(ADMIN + "/" + sf + "/banners"), editor(A), wrongKind, 400);
        Map<String, Object> badUrl = new HashMap<>(ok); badUrl.put("destinationUrl", "javascript:alert(1)");
        call(post(ADMIN + "/" + sf + "/banners"), editor(A), badUrl, 400);
        Map<String, Object> noAlt = new HashMap<>(ok); noAlt.remove("altText");
        call(post(ADMIN + "/" + sf + "/banners"), editor(A), noAlt, 400);
        Map<String, Object> badDates = new HashMap<>(ok); badDates.put("startsAt", OffsetDateTime.now().plusDays(2).toString()); badDates.put("endsAt", OffsetDateTime.now().plusDays(1).toString());
        call(post(ADMIN + "/" + sf + "/banners"), editor(A), badDates, 400);
        Map<String, Object> foreignCampaign = new HashMap<>(ok); foreignCampaign.put("campaignId", 424242);
        call(post(ADMIN + "/" + sf + "/banners"), editor(A), foreignCampaign, 400);
        call(post(ADMIN + "/" + sfB + "/banners"), editor(B), ok, 400); // media belongs to A's storefront

        long live = call(post(ADMIN + "/" + sf + "/banners"), editor(A), ok, 201).get("id").asLong();
        Map<String, Object> future = new HashMap<>(ok); future.put("name", "Future"); future.put("startsAt", OffsetDateTime.now().plusDays(5).toString()); future.put("priority", 5);
        long futureId = call(post(ADMIN + "/" + sf + "/banners"), editor(A), future, 201).get("id").asLong();
        Map<String, Object> off = new HashMap<>(ok); off.put("name", "Off"); off.put("active", false);
        call(post(ADMIN + "/" + sf + "/banners"), editor(A), off, 201);
        Map<String, Object> expired = new HashMap<>(ok); expired.put("name", "Expired"); expired.put("startsAt", OffsetDateTime.now().minusDays(5).toString()); expired.put("endsAt", OffsetDateTime.now().minusDays(1).toString());
        call(post(ADMIN + "/" + sf + "/banners"), editor(A), expired, 201);
        JsonNode visible = pub("/ban/banners", 200);
        assertEquals(1, visible.size());
        assertEquals("Spring", visible.get(0).get("name").asText());
        assertTrue(visible.get(0).get("desktopImageUrl").asText().startsWith(StorefrontRenderer.MEDIA_PATH));
        assertEquals(4, call(get(ADMIN + "/" + sf + "/banners"), editor(A), null, 200).size());

        call(post(ADMIN + "/" + sf + "/banners/reorder"), editor(A), Map.of("ids", List.of(live, futureId)), 200);
        call(post(ADMIN + "/" + sf + "/banners/reorder"), editor(A), Map.of("ids", List.of(live, live)), 400);
        ok.put("name", "Renamed");
        assertEquals("Renamed", call(put(ADMIN + "/banners/" + live), editor(A), ok, 200).get("name").asText());
        call(put(ADMIN + "/banners/" + live), editor(B), ok, 404);
        mvc.perform(delete(ADMIN + "/banners/" + live).header(HttpHeaders.AUTHORIZATION, "Bearer " + editor(B))).andExpect(status().isNotFound());
        mvc.perform(delete(ADMIN + "/banners/" + live).header(HttpHeaders.AUTHORIZATION, "Bearer " + editor(A))).andExpect(status().isNoContent());
        assertEquals(0, pub("/ban/banners", 200).size());
    }

    @Test @DisplayName("domains: validation, real verification outcome, SSL only after verification, primary only when verified")
    void domains() throws Exception {
        long sf = storefront(A, "dom");
        for (String bad : List.of("localhost", "192.168.0.1", "no_underscore.example.com", "single", "-bad.example.com", "x.shops.test", "shop.local", "http://shop.example.com"))
            call(post(ADMIN + "/" + sf + "/domains"), editor(A), Map.of("hostname", bad), 400);
        JsonNode d = call(post(ADMIN + "/" + sf + "/domains"), editor(A), Map.of("hostname", "Shop.Example.COM."), 201);
        assertEquals("shop.example.com", d.get("hostname").asText());
        assertEquals("PENDING", d.get("verificationStatus").asText());
        assertEquals("NOT_CHECKED", d.get("sslStatus").asText());
        assertEquals("_ucp-challenge.shop.example.com", d.at("/instructions/step1/name").asText());
        String token = d.get("verificationToken").asText();
        long id = d.get("id").asLong();
        call(post(ADMIN + "/" + storefront(B, "dom-b") + "/domains"), editor(B), Map.of("hostname", "shop.example.com"), 409); // globally unique
        call(post(ADMIN + "/domains/" + id + "/verify"), editor(B), null, 404);

        call(post(ADMIN + "/domains/" + id + "/check-ssl"), editor(A), null, 409); // unverified
        call(post(ADMIN + "/domains/" + id + "/primary"), editor(A), null, 409);
        when(dns.txtRecords("_ucp-challenge.shop.example.com")).thenReturn(List.of());
        assertEquals("FAILED", call(post(ADMIN + "/domains/" + id + "/verify"), editor(A), null, 200).get("verificationStatus").asText());
        when(dns.txtRecords("_ucp-challenge.shop.example.com")).thenReturn(List.of("ucp-verify=wrongtoken"));
        assertEquals("FAILED", call(post(ADMIN + "/domains/" + id + "/verify"), editor(A), null, 200).get("verificationStatus").asText());
        when(dns.txtRecords("_ucp-challenge.shop.example.com")).thenThrow(new RuntimeException("NXDOMAIN"));
        JsonNode failed = call(post(ADMIN + "/domains/" + id + "/verify"), editor(A), null, 200);
        assertEquals("FAILED", failed.get("verificationStatus").asText());
        assertTrue(failed.get("lastCheckMessage").asText().contains("DNS lookup failed"));
        pub("/resolve?host=shop.example.com", 404); // not verified -> never resolves

        org.mockito.Mockito.reset(dns);
        when(dns.txtRecords("_ucp-challenge.shop.example.com")).thenReturn(List.of("ucp-verify=" + token));
        JsonNode verified = call(post(ADMIN + "/domains/" + id + "/verify"), editor(A), null, 200);
        assertEquals("VERIFIED", verified.get("verificationStatus").asText());
        assertNotNull(verified.get("verifiedAt"));
        assertEquals("dom", pub("/resolve?host=SHOP.example.com", 200).get("slug").asText());

        when(tls.probe("shop.example.com")).thenReturn(new DomainService.TlsResult(false, null, "TLS check failed: SSLHandshakeException"));
        JsonNode sslFail = call(post(ADMIN + "/domains/" + id + "/check-ssl"), editor(A), null, 200);
        assertEquals("FAILED", sslFail.get("sslStatus").asText());
        when(tls.probe("shop.example.com")).thenReturn(new DomainService.TlsResult(true, OffsetDateTime.now().plusDays(60), "Certificate valid"));
        JsonNode sslOk = call(post(ADMIN + "/domains/" + id + "/check-ssl"), editor(A), null, 200);
        assertEquals("ACTIVE", sslOk.get("sslStatus").asText());
        assertNotNull(sslOk.get("sslExpiresAt"));

        call(post(ADMIN + "/domains/" + id + "/primary"), editor(A), null, 200);
        assertTrue(pub("/dom", 200).get("baseUrl").asText().equals("https://shop.example.com"));
        long second = call(post(ADMIN + "/" + sf + "/domains"), editor(A), Map.of("hostname", "www.example.org"), 201).get("id").asLong();
        call(post(ADMIN + "/domains/" + second + "/primary"), editor(A), null, 409);
        JsonNode listing = call(get(ADMIN + "/" + sf + "/domains"), editor(A), null, 200);
        assertEquals("PLATFORM_SUBDOMAIN", listing.get(0).get("type").asText());
        assertEquals("dom.shops.test", listing.get(0).get("hostname").asText());
        assertEquals(3, listing.size());
        mvc.perform(delete(ADMIN + "/domains/" + second).header(HttpHeaders.AUTHORIZATION, "Bearer " + editor(A))).andExpect(status().isNoContent());
    }

    @Test @DisplayName("dashboard reports real counts")
    void dashboard() throws Exception {
        long sf = storefront(A, "dash");
        JsonNode empty = call(get(ADMIN + "/" + sf + "/dashboard"), editor(A), null, 200);
        assertEquals(0, empty.get("publishedPages").asInt());
        publishNow(A, doc(A, sf, "HOMEPAGE"), home("Hi"));
        call(post(ADMIN + "/" + sf + "/pages"), editor(A), Map.of("slug", "about", "title", "About"), 201);
        JsonNode d = call(get(ADMIN + "/" + sf + "/dashboard"), editor(A), null, 200);
        assertEquals(1, d.get("publishedPages").asInt());
        assertEquals(1, d.get("landingPages").asInt());
        call(get(ADMIN + "/" + sf + "/dashboard"), editor(B), null, 404);
    }
}

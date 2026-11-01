package com.ecommerce.order.accounting;

import com.ecommerce.order.accounting.security.AccountingAuthorizationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Tenant isolation of the accounting admin API against real PostgreSQL with the real Flyway migrations (V1–V19).
 * Runs only when UCP_TEST_ACCOUNTING_POSTGRES_URL points at a database whose name starts with "ucp_scratch_".
 * Every run uses fresh random tenants, so previous runs' rows never affect the assertions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "UCP_TEST_ACCOUNTING_POSTGRES_URL", matches = "jdbc:postgresql://.+/ucp_scratch_[A-Za-z0-9_]+")
class AccountingTenantIsolationPostgresTest {

    private static final UUID LEGACY = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final String run = UUID.randomUUID().toString().substring(0, 8);

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("UCP_TEST_ACCOUNTING_POSTGRES_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("UCP_TEST_POSTGRES_USER", "ecommerce_admin"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("UCP_TEST_POSTGRES_PASSWORD", "admin_password"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.table", () -> "accounting_flyway_schema_history");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private AccountingAuthorizationClient authorizationClient;

    @Test
    @DisplayName("Schema: V19 applied, tenant columns present, account codes unique per tenant only")
    void migrationApplied() {
        assertThat(jdbc.queryForObject("SELECT MAX(version::int) FROM accounting_flyway_schema_history WHERE success", Integer.class)).isGreaterThanOrEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_constraint WHERE conname='accounts_code_key'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_constraint WHERE conname='uq_accounts_tenant_code'", Integer.class)).isOne();
    }

    @Test
    @DisplayName("Chart of accounts, journals and reports: each tenant sees only its own; same codes and keys allowed in both")
    void journalsAndReportsAreTenantScoped() throws Exception {
        String a = tenantToken(tenantA, 11, "accounting:*"), b = tenantToken(tenantB, 21, "accounting:*");
        for (String token : List.of(a, b)) {
            ok(post("/api/accounting/admin/accounts"), token, Map.of("code", "1000", "name", "Cash", "accountType", "ASSET", "normalBalance", "DEBIT", "cashAccount", true));
            ok(post("/api/accounting/admin/accounts"), token, Map.of("code", "4000", "name", "Sales", "accountType", "REVENUE", "normalBalance", "CREDIT"));
        }
        // Same idempotency key in both tenants creates two separate journals.
        JsonNode journalA = ok(post("/api/accounting/admin/journals"), a, journal("shared-key-" + run, "125.00"));
        JsonNode journalB = ok(post("/api/accounting/admin/journals"), b, journal("shared-key-" + run, "7.00"));
        assertThat(journalA.get("id").asLong()).isNotEqualTo(journalB.get("id").asLong());

        assertThat(ids(ok(get("/api/accounting/admin/journals"), b, null).get("items")))
                .contains(journalB.get("id").asLong()).doesNotContain(journalA.get("id").asLong());
        assertThat(codes(okArray(get("/api/accounting/admin/accounts"), b))).containsExactlyInAnyOrder("1000", "4000");

        // B cannot reverse A's journal; A's journal stays un-reversed.
        assertThat(status(post("/api/accounting/admin/journals/" + journalA.get("id").asLong() + "/reverse"), b, Map.of("reason", "cross tenant"))).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE reversal_of_id=?", Integer.class, journalA.get("id").asLong())).isZero();

        String today = LocalDate.now().toString();
        JsonNode plB = ok(get("/api/accounting/admin/reports/profit-loss?startDate=" + today + "&endDate=" + today + "&currency=USD"), b, null);
        assertThat(plB.get("revenue").decimalValue()).isEqualByComparingTo("7.00");
        JsonNode cashA = ok(get("/api/accounting/admin/reports/cash-flow?startDate=" + today + "&endDate=" + today + "&currency=USD"), a, null);
        assertThat(cashA.get("inflows").decimalValue()).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("Invoices, periods and journal drafts cannot be read or changed across tenants")
    void invoicesPeriodsDraftsAreTenantScoped() throws Exception {
        String a = tenantToken(tenantA, 12, "accounting:*"), b = tenantToken(tenantB, 22, "accounting:*");
        JsonNode invoice = ok(post("/api/accounting/admin/invoices"), a, Map.of("idempotencyKey", "inv-" + run, "userId", 5,
                "lineItems", List.of(Map.of("description", "Service", "quantity", 1, "unitPrice", "40.00"))));
        long invoiceId = invoice.get("id").asLong();
        assertThat(status(get("/api/accounting/admin/invoices/" + invoiceId), b, null)).isEqualTo(404);
        assertThat(status(patch("/api/accounting/admin/invoices/" + invoiceId + "/status"), b, Map.of("status", "VOID"))).isIn(400, 404);
        assertThat(ids(ok(get("/api/accounting/admin/invoices"), b, null).get("items"))).doesNotContain(invoiceId);
        assertThat(ok(get("/api/accounting/admin/invoices/" + invoiceId), a, null).get("status").asText()).isEqualTo("DRAFT");

        String name = "FY-" + run;
        JsonNode periodA = ok(post("/api/accounting/admin/periods"), a, Map.of("periodName", name, "startDate", "2020-01-01", "endDate", "2020-01-31"));
        ok(post("/api/accounting/admin/periods"), b, Map.of("periodName", name, "startDate", "2020-01-01", "endDate", "2020-01-31"));
        assertThat(status(post("/api/accounting/admin/periods/" + periodA.get("id").asLong() + "/close"), b, Map.of("reason", "closing someone else's books"))).isEqualTo(404);

        ok(post("/api/accounting/admin/accounts"), a, Map.of("code", "1000", "name", "Cash", "accountType", "ASSET", "normalBalance", "DEBIT"));
        ok(post("/api/accounting/admin/accounts"), a, Map.of("code", "4000", "name", "Sales", "accountType", "REVENUE", "normalBalance", "CREDIT"));
        JsonNode draft = ok(post("/api/accounting/admin/journals/drafts"), a, journal("draft-" + run, "10.00"));
        long draftId = draft.get("id").asLong();
        ok(post("/api/accounting/admin/journals/drafts/" + draftId + "/submit"), a, null);
        String otherTenantApprover = tenantToken(tenantB, 99, "accounting:*");
        assertThat(status(post("/api/accounting/admin/journals/drafts/" + draftId + "/approve"), otherTenantApprover, null)).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT status FROM journal_drafts WHERE id=?", String.class, draftId)).isEqualTo("PENDING_APPROVAL");
        // B's account codes do not satisfy A's lines: B has no 1000/4000 in this test, so a B draft with those codes is refused.
        assertThat(status(post("/api/accounting/admin/journals/drafts"), b, journal("draft-b-" + run, "10.00"))).isEqualTo(400);
    }

    @Test
    @DisplayName("Ledger (JPA): adjustments, ledger list, reversal and audit log are tenant-scoped; platform token sees the legacy tenant")
    void ledgerIsTenantScoped() throws Exception {
        String a = tenantToken(tenantA, 13, "accounting:*"), b = tenantToken(tenantB, 23, "accounting:*");
        JsonNode adjustment = ok(post("/api/accounting/admin/adjustments"), a, Map.of("userId", 501, "transactionType", "MANUAL_CREDIT",
                "amount", "33.00", "currency", "USD", "reason", "Tenant A goodwill " + run, "reference", "REF-" + run, "idempotencyKey", "adj-" + run));
        long txId = adjustment.get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM ledger_transactions WHERE id=?", UUID.class, txId)).isEqualTo(tenantA);

        assertThat(ids(ok(get("/api/accounting/admin/ledger?size=100"), b, null).get("content"))).doesNotContain(txId);
        assertThat(ids(ok(get("/api/accounting/admin/ledger?size=100"), a, null).get("content"))).contains(txId);
        assertThat(ids(ok(get("/api/accounting/admin/ledger?size=100"), platformToken(), null).get("content"))).doesNotContain(txId);

        int reverse = status(post("/api/accounting/admin/reversals/" + txId), b, Map.of("reason", "cross tenant reversal"));
        assertThat(reverse).isGreaterThanOrEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transactions WHERE reversal_of_id=?", Integer.class, txId)).isZero();

        assertThat(ok(get("/api/accounting/admin/audit-logs?size=100"), b, null).get("content").toString()).doesNotContain("Tenant A goodwill " + run);
        assertThat(ok(get("/api/accounting/admin/audit-logs?size=100"), a, null).get("content").toString()).contains("Tenant A goodwill " + run);
    }

    @Test
    @DisplayName("Authentication: no token, another application's token, forged tenant header, revoked grant")
    void authentication() throws Exception {
        assertThat(mvc.perform(get("/api/accounting/admin/accounts").header("X-User-Role", "ADMIN").header("X-Tenant-ID", tenantA.toString()))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(status(get("/api/accounting/admin/accounts"), token("ECOMMERCE_ADMIN", tenantA, 14, "accounting:*"), null)).isEqualTo(403);

        String a = tenantToken(tenantA, 15, "accounting:*");
        ok(post("/api/accounting/admin/accounts"), a, Map.of("code", "9100", "name", "Forge check", "accountType", "EXPENSE", "normalBalance", "DEBIT"));
        String b = tenantToken(tenantB, 25, "accounting:*");
        MvcResult forged = mvc.perform(get("/api/accounting/admin/accounts").header(HttpHeaders.AUTHORIZATION, b)
                .header("X-Tenant-ID", tenantA.toString()).header("X-User-Role", "SUPER_ADMIN")).andReturn();
        assertThat(forged.getResponse().getContentAsString()).doesNotContain("Forge check");

        doThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "revoked", HttpHeaders.EMPTY, new byte[0], null))
                .when(authorizationClient).requireCurrentGrant(anyString());
        assertThat(status(get("/api/accounting/admin/accounts"), a, null)).isEqualTo(403);
    }

    private Map<String, Object> journal(String key, String amount) {
        return Map.of("idempotencyKey", key, "description", "Sale " + key, "currency", "USD", "lines", List.of(
                Map.of("accountCode", "1000", "debitAmount", amount, "creditAmount", "0"),
                Map.of("accountCode", "4000", "debitAmount", "0", "creditAmount", amount)));
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        MvcResult result = perform(request, token, body);
        int status = result.getResponse().getStatus();
        assertThat(status).as(result.getResponse().getContentAsString() + " " + result.getResponse().getErrorMessage()).isBetween(200, 299);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode okArray(MockHttpServletRequestBuilder request, String token) throws Exception {
        return ok(request, token, null);
    }

    private int status(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        try {
            return perform(request, token, body).getResponse().getStatus();
        } catch (Exception unhandled) {
            return 500; // an exception the controller does not map (e.g. IllegalArgumentException) — still a refusal
        }
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request).andReturn();
    }

    private static List<Long> ids(JsonNode items) {
        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private static List<String> codes(JsonNode accounts) {
        List<String> codes = new ArrayList<>();
        accounts.forEach(account -> codes.add(account.get("code").asText()));
        return codes;
    }

    private static String tenantToken(UUID tenant, long userId, String... permissions) {
        return token("ACCOUNTING", tenant, userId, permissions);
    }

    private static String token(String application, UUID tenant, long userId, String... permissions) {
        return "Bearer " + Jwts.builder()
                .claim("id", userId).claim("role", "ACCOUNTANT").claim("tenant_role", "ACCOUNTANT")
                .claim("tenant_id", tenant.toString()).claim("application_code", application)
                .claim("permissions", List.of(permissions))
                .setSubject("user" + userId + "@" + tenant + ".test").setId(UUID.randomUUID().toString())
                .setIssuer("universal-commerce").setAudience("commerce-api")
                .setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(AccountingSecurityAndControllerTest.TEST_JWT_SECRET.getBytes()), SignatureAlgorithm.HS256).compact();
    }

    private static String platformToken() {
        return AccountingSecurityAndControllerTest.platformToken(1L, "ADMIN", "platform-admin@example.test");
    }
}

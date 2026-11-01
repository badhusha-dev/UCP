package com.ecommerce.product;

import com.ecommerce.product.repository.ProductAuditLogRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogAdminTenantIsolationTest {
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductAuditLogRepository auditRepository;
    @MockBean private CatalogAuthorizationClient authorizationClient;

    @Value("${jwt.secret}") private String secret;

    @BeforeEach
    void reset() {
        auditRepository.deleteAll();
        productRepository.deleteAll();
        doNothing().when(authorizationClient).requireCurrentGrant(anyString());
    }

    private String token(UUID tenant, String application, List<String> permissions) {
        return token(tenant, application, permissions, "universal-commerce", new Date(System.currentTimeMillis() + 600_000));
    }

    private String token(UUID tenant, String application, List<String> permissions, String issuer, Date expiry) {
        return Jwts.builder().setSubject("admin@" + tenant + ".example").setId(UUID.randomUUID().toString())
                .setIssuer(issuer).setAudience("commerce-api").setExpiration(expiry)
                .claim("id", 7).claim("tenant_id", tenant.toString()).claim("tenant_role", "CATALOG_MANAGER")
                .claim("application_code", application).claim("permissions", permissions)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
    }

    private String admin(UUID tenant) {
        return token(tenant, "ECOMMERCE_ADMIN", List.of("catalog:read", "catalog:manage"));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long createProduct(String token, String sku, String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"sku\":\"" + sku + "\",\"price\":19.99}";
        MvcResult result = mockMvc.perform(withToken(post("/api/products/admin"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    @Test
    @DisplayName("admin API rejects missing, foreign-application, expired and wrong-issuer tokens")
    void rejectsInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/products/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(get("/api/products/admin"), token(TENANT_A, "INVENTORY", List.of("catalog:read"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(get("/api/products/admin"), token(TENANT_A, "ECOMMERCE_ADMIN", List.of("catalog:read"),
                "universal-commerce", new Date(System.currentTimeMillis() - 1000)))).andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(get("/api/products/admin"), token(TENANT_A, "ECOMMERCE_ADMIN", List.of("catalog:read"),
                "someone-else", new Date(System.currentTimeMillis() + 600_000)))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("tenant B cannot read, update, archive, restore or list tenant A products")
    void tenantsAreIsolated() throws Exception {
        long id = createProduct(admin(TENANT_A), "SKU-1", "Tenant A Widget");
        String b = admin(TENANT_B);

        mockMvc.perform(withToken(get("/api/products/admin/" + id), b)).andExpect(status().isNotFound());
        mockMvc.perform(withToken(get("/api/products/admin/" + id + "/history"), b)).andExpect(status().isNotFound());
        mockMvc.perform(withToken(put("/api/products/admin/" + id), b).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijacked\",\"price\":1}")).andExpect(status().isNotFound());
        mockMvc.perform(withToken(delete("/api/products/admin/" + id), b)).andExpect(status().isNotFound());
        mockMvc.perform(withToken(patch("/api/products/admin/" + id + "/restore"), b)).andExpect(status().isNotFound());
        mockMvc.perform(withToken(get("/api/products/admin"), b)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(withToken(get("/api/products/admin"), admin(TENANT_A))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Tenant A Widget"))
                .andExpect(jsonPath("$.content[0].tenantId").doesNotExist());
        assertThat(productRepository.findById(id).orElseThrow().getName()).isEqualTo("Tenant A Widget");
    }

    @Test
    @DisplayName("SKUs are unique per tenant, not globally")
    void skuUniquePerTenant() throws Exception {
        createProduct(admin(TENANT_A), "SHARED-1", "A product");
        createProduct(admin(TENANT_B), "shared-1", "B product");
        mockMvc.perform(withToken(post("/api/products/admin"), admin(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Dup\",\"sku\":\"Shared-1\",\"price\":2}")).andExpect(status().isConflict());
        mockMvc.perform(withToken(post("/api/products/admin"), admin(TENANT_A)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad\",\"sku\":\"has space\",\"price\":2}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("read-only permission cannot mutate; missing permission cannot read")
    void permissionsAreEnforcedInTheService() throws Exception {
        long id = createProduct(admin(TENANT_A), "P-1", "Item");
        String readOnly = token(TENANT_A, "ECOMMERCE_ADMIN", List.of("catalog:read"));
        String none = token(TENANT_A, "ECOMMERCE_ADMIN", List.of("orders:read"));

        mockMvc.perform(withToken(get("/api/products/admin/" + id), readOnly)).andExpect(status().isOk());
        mockMvc.perform(withToken(post("/api/products/admin"), readOnly).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"price\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(withToken(put("/api/products/admin/" + id), readOnly).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"price\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(withToken(delete("/api/products/admin/" + id), readOnly)).andExpect(status().isForbidden());
        mockMvc.perform(withToken(get("/api/products/admin"), none)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("revoked grant is rejected and an identity outage fails closed")
    void revokedGrantAndOutage() throws Exception {
        doThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "revoked", HttpHeaders.EMPTY, new byte[0], null))
                .when(authorizationClient).requireCurrentGrant(anyString());
        mockMvc.perform(withToken(get("/api/products/admin"), admin(TENANT_A))).andExpect(status().isForbidden());

        doThrow(new ResourceAccessException("identity down")).when(authorizationClient).requireCurrentGrant(anyString());
        mockMvc.perform(withToken(get("/api/products/admin"), admin(TENANT_A))).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("changes are audited with actor and before/after, and archive is idempotent")
    void changesAreAudited() throws Exception {
        String a = admin(TENANT_A);
        long id = createProduct(a, "AUD-1", "Original");
        mockMvc.perform(withToken(put("/api/products/admin/" + id), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"sku\":\"AUD-1\",\"price\":25.5}")).andExpect(status().isOk());
        mockMvc.perform(withToken(delete("/api/products/admin/" + id), a)).andExpect(status().isNoContent());
        mockMvc.perform(withToken(delete("/api/products/admin/" + id), a)).andExpect(status().isNoContent());

        MvcResult result = mockMvc.perform(withToken(get("/api/products/admin/" + id + "/history"), a))
                .andExpect(status().isOk()).andReturn();
        JsonNode history = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(history).hasSize(3);
        assertThat(history.get(0).get("action").asText()).isEqualTo("ARCHIVE");
        assertThat(history.get(1).get("action").asText()).isEqualTo("UPDATE");
        assertThat(history.get(1).get("beforeValue").asText()).contains("Original");
        assertThat(history.get(1).get("afterValue").asText()).contains("Renamed");
        assertThat(history.get(2).get("action").asText()).isEqualTo("CREATE");
        assertThat(history.get(2).get("actorId").asLong()).isEqualTo(7);

        mockMvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("inventory analytics aggregates only the caller's tenant")
    void inventoryAnalyticsIsTenantScoped() throws Exception {
        createProduct(admin(TENANT_A), "A-1", "Tenant A product");
        createProduct(admin(TENANT_B), "B-1", "Tenant B product one");
        createProduct(admin(TENANT_B), "B-2", "Tenant B product two");

        String analystA = token(TENANT_A, "ECOMMERCE_ADMIN", List.of("catalog:read", "analytics:read"));
        mockMvc.perform(withToken(get("/api/products/admin/analytics/inventory"), analystA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(1))
                .andExpect(jsonPath("$.lowStockItems[*].name").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.startsWith("Tenant A"))));
    }

    @Test
    @DisplayName("inventory analytics requires analytics:read and ignores a forged role header")
    void inventoryAnalyticsRequiresPermission() throws Exception {
        mockMvc.perform(withToken(get("/api/products/admin/analytics/inventory"), admin(TENANT_A))
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isForbidden());
    }
}

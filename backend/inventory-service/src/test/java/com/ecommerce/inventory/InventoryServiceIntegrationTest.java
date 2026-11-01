package com.ecommerce.inventory;

import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.model.InventoryItem;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.inventory.repository.StockMovementRepository;
import com.ecommerce.inventory.repository.StockReservationRepository;
import com.ecommerce.inventory.security.InventoryAuthorizationClient;
import com.ecommerce.inventory.service.InventoryProducerService;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class InventoryServiceIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final String EXTERNAL_DATABASE_URL = System.getenv("UCP_TEST_INVENTORY_POSTGRES_URL");

    @DynamicPropertySource
    static void optionalPostgresProperties(DynamicPropertyRegistry properties) {
        if (EXTERNAL_DATABASE_URL == null || EXTERNAL_DATABASE_URL.isBlank()) return;
        properties.add("spring.datasource.url", () -> EXTERNAL_DATABASE_URL);
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("UCP_TEST_INVENTORY_POSTGRES_USERNAME", "postgres"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("UCP_TEST_INVENTORY_POSTGRES_PASSWORD", ""));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @MockBean
    private InventoryProducerService inventoryProducerService;

    @MockBean
    private InventoryAuthorizationClient authorizationClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        stockReservationRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryItemRepository.deleteAll();

        inventoryItemRepository.save(InventoryItem.builder()
                .tenantId(TENANT_A)
                .productId(101L)
                .sku("TEST-SKU-101")
                .stockQuantity(50)
                .reservedQuantity(0)
                .reorderThreshold(10)
                .costPrice(new BigDecimal("25.0000"))
                .warehouseLocation("TEST-WH-01")
                .build());
    }

    @Test
    void testGetInventoryAvailability() throws Exception {
        mockMvc.perform(get("/api/inventory/101").header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId", is(101)))
                .andExpect(jsonPath("$.stockQuantity", is(50)))
                .andExpect(jsonPath("$.availableQuantity", is(50)))
                .andExpect(jsonPath("$.lowStock", is(false)));
    }

    @Test
    void testAtomicReservationAndConfirmationLifecycle() throws Exception {
        ReserveStockRequest reserveReq = ReserveStockRequest.builder()
                .productId(101L)
                .quantity(5)
                .orderId(999L)
                .holdDurationMinutes(15)
                .build();

        String resJson = mockMvc.perform(post("/api/inventory/reservations")
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("HOLD")))
                .andExpect(jsonPath("$.quantity", is(5)))
                .andReturn().getResponse().getContentAsString();

        StockReservationResponse resDto = objectMapper.readValue(resJson, StockReservationResponse.class);
        assertNotNull(resDto.getReservationId());

        // Check reserved quantity in inventory
        InventoryItem itemAfterHold = inventoryItemRepository.findByTenantIdAndProductId(TENANT_A, 101L).orElseThrow();
        assertEquals(50, itemAfterHold.getStockQuantity());
        assertEquals(5, itemAfterHold.getReservedQuantity());
        assertEquals(45, itemAfterHold.getAvailableQuantity());

        // Confirm reservation
        mockMvc.perform(post("/api/inventory/reservations/" + resDto.getReservationId() + "/confirm")
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage")))
                        .param("orderId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        InventoryItem itemAfterConfirm = inventoryItemRepository.findByTenantIdAndProductId(TENANT_A, 101L).orElseThrow();
        assertEquals(45, itemAfterConfirm.getStockQuantity());
        assertEquals(0, itemAfterConfirm.getReservedQuantity());
        assertEquals(45, itemAfterConfirm.getAvailableQuantity());
    }

    @Test
    void testAtomicReservationInsufficientStockThrowsConflict() throws Exception {
        ReserveStockRequest reserveReq = ReserveStockRequest.builder()
                .productId(101L)
                .quantity(100) // Exceeds 50
                .build();

        mockMvc.perform(post("/api/inventory/reservations")
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void testReleaseReservationCompensatesStock() throws Exception {
        ReserveStockRequest reserveReq = ReserveStockRequest.builder()
                .productId(101L)
                .quantity(10)
                .build();

        String resJson = mockMvc.perform(post("/api/inventory/reservations")
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        StockReservationResponse resDto = objectMapper.readValue(resJson, StockReservationResponse.class);

        // Release reservation
        mockMvc.perform(post("/api/inventory/reservations/" + resDto.getReservationId() + "/release")
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage"))))
                .andExpect(status().isNoContent());

        InventoryItem itemAfterRelease = inventoryItemRepository.findByTenantIdAndProductId(TENANT_A, 101L).orElseThrow();
        assertEquals(50, itemAfterRelease.getStockQuantity());
        assertEquals(0, itemAfterRelease.getReservedQuantity());
        assertEquals(50, itemAfterRelease.getAvailableQuantity());
    }

    @Test
    void testAdminStockAdjustmentAuthorized() throws Exception {
        StockAdjustmentRequest adj = StockAdjustmentRequest.builder()
                .productId(101L)
                .delta(20)
                .movementType("RECEIVE")
                .reason("Inbound supplier shipment")
                .build();

        mockMvc.perform(post("/api/inventory/admin/adjust")
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adj)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity", is(70)))
                .andExpect(jsonPath("$.availableQuantity", is(70)));

        assertEquals(1, stockMovementRepository.count());
    }

    @Test
    void testAdminEndpointsForbiddenForNonStaff() throws Exception {
        StockAdjustmentRequest adj = StockAdjustmentRequest.builder()
                .productId(101L)
                .delta(10)
                .movementType("ADJUSTMENT")
                .build();

        mockMvc.perform(post("/api/inventory/admin/adjust")
                        .header("Authorization", inventoryToken("CUSTOMER", Set.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adj)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCsvExportSanitizesFormulaInjection() throws Exception {
        // Create an item with dangerous characters in SKU
        inventoryItemRepository.save(InventoryItem.builder()
                .tenantId(TENANT_A)
                .productId(102L)
                .sku("=cmd|'/C calc'!A0")
                .stockQuantity(10)
                .reorderThreshold(5)
                .costPrice(BigDecimal.TEN)
                .warehouseLocation("+WH-DANGEROUS")
                .build());

        String csv = mockMvc.perform(get("/api/inventory/admin/export")
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Formula injection prefixes should be sanitized with leading quote
        assertTrue(csv.contains("'=cmd"));
        assertTrue(csv.contains("'+WH-DANGEROUS"));
    }

    @Test
    void testInventoryListCannotReadAnotherTenantOrTrustSpoofedHeaders() throws Exception {
        inventoryItemRepository.save(InventoryItem.builder()
                .tenantId(TENANT_B)
                .productId(101L)
                .sku("CLIENT-B-SECRET")
                .stockQuantity(999)
                .reservedQuantity(0)
                .reorderThreshold(10)
                .costPrice(new BigDecimal("99.0000"))
                .warehouseLocation("CLIENT-B")
                .build());

        mockMvc.perform(get("/api/inventory/admin/items?tenantId=" + TENANT_B)
                        .header("X-Tenant-ID", TENANT_B.toString())
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read", "inventory:manage"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stockQuantity", is(50)))
                .andExpect(content().string(not(containsString("CLIENT-B-SECRET"))));

        mockMvc.perform(get("/api/inventory/101")
                        .header("X-Tenant-ID", TENANT_B.toString())
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity", is(50)))
                .andExpect(content().string(not(containsString("CLIENT-B-SECRET"))));

        mockMvc.perform(get("/api/inventory/admin/items")
                        .header("X-User-Role", "ADMIN")
                        .header("X-Tenant-ID", TENANT_B.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRevokedApplicationGrantIsDeniedByInventory() throws Exception {
        org.mockito.Mockito.doThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN))
                .when(authorizationClient).requireCurrentGrant(org.mockito.ArgumentMatchers.anyString());
        mockMvc.perform(get("/api/inventory/admin/items")
                        .header("Authorization", inventoryToken("WAREHOUSE_OPERATOR", Set.of("inventory:read"))))
                .andExpect(status().isForbidden());
    }

    private String inventoryToken(String role, Set<String> permissions) {
        Date now = new Date();
        return "Bearer " + Jwts.builder()
                .setClaims(Map.of("id", 17L, "role", role, "tenant_role", role, "tenant_id", TENANT_A.toString(),
                        "application_code", "INVENTORY", "permissions", permissions))
                .setSubject("employee@example.test")
                .setId(UUID.randomUUID().toString())
                .setIssuer("universal-commerce")
                .setAudience("commerce-api")
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 600_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }
}

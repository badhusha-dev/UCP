package com.ecommerce.stock;

import com.ecommerce.stock.dto.*;
import com.ecommerce.stock.model.StockItem;
import com.ecommerce.stock.repository.StockItemRepository;
import com.ecommerce.stock.repository.StockMovementRepository;
import com.ecommerce.stock.repository.StockReservationRepository;
import com.ecommerce.stock.service.StockProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class StockServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @MockBean
    private StockProducerService stockProducerService;

    @BeforeEach
    void setUp() {
        stockReservationRepository.deleteAll();
        stockMovementRepository.deleteAll();
        stockItemRepository.deleteAll();

        stockItemRepository.save(StockItem.builder()
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
        mockMvc.perform(get("/api/stock/101"))
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

        String resJson = mockMvc.perform(post("/api/stock/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("HOLD")))
                .andExpect(jsonPath("$.quantity", is(5)))
                .andReturn().getResponse().getContentAsString();

        StockReservationResponse resDto = objectMapper.readValue(resJson, StockReservationResponse.class);
        assertNotNull(resDto.getReservationId());

        // Check reserved quantity in inventory
        StockItem itemAfterHold = stockItemRepository.findByProductId(101L).orElseThrow();
        assertEquals(50, itemAfterHold.getStockQuantity());
        assertEquals(5, itemAfterHold.getReservedQuantity());
        assertEquals(45, itemAfterHold.getAvailableQuantity());

        // Confirm reservation
        mockMvc.perform(post("/api/stock/reservations/" + resDto.getReservationId() + "/confirm")
                        .param("orderId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        StockItem itemAfterConfirm = stockItemRepository.findByProductId(101L).orElseThrow();
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

        mockMvc.perform(post("/api/stock/reservations")
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

        String resJson = mockMvc.perform(post("/api/stock/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reserveReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        StockReservationResponse resDto = objectMapper.readValue(resJson, StockReservationResponse.class);

        // Release reservation
        mockMvc.perform(post("/api/stock/reservations/" + resDto.getReservationId() + "/release"))
                .andExpect(status().isNoContent());

        StockItem itemAfterRelease = stockItemRepository.findByProductId(101L).orElseThrow();
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

        mockMvc.perform(post("/api/stock/admin/adjust")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Email", "admin@ecommerce.com")
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

        mockMvc.perform(post("/api/stock/admin/adjust")
                        .header("X-User-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adj)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCsvExportContainsOnlyStockCounts() throws Exception {
        // Catalog text (SKU, location) is owned by inventory-service and must not appear in the stock-count export.
        stockItemRepository.save(StockItem.builder()
                .productId(102L)
                .sku("=cmd|'/C calc'!A0")
                .stockQuantity(10)
                .reorderThreshold(5)
                .costPrice(BigDecimal.TEN)
                .warehouseLocation("+WH-DANGEROUS")
                .build());

        String csv = mockMvc.perform(get("/api/stock/admin/export")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(csv.startsWith("productId,stockQuantity,reservedQuantity,availableQuantity\r\n"));
        assertTrue(csv.contains("\"102\",\"10\",\"0\",\"10\"\r\n"));
        assertFalse(csv.contains("cmd"));
        assertFalse(csv.contains("WH-DANGEROUS"));
    }

    @Test
    void testCsvCellSanitizerNeutralizesFormulaPrefixes() {
        assertEquals("\"'=cmd|'/C calc'!A0\"", com.ecommerce.stock.service.StockService.sanitizeForCsv("=cmd|'/C calc'!A0"));
        assertEquals("\"'+WH-DANGEROUS\"", com.ecommerce.stock.service.StockService.sanitizeForCsv("+WH-DANGEROUS"));
        assertEquals("\"'@SUM(A1)\"", com.ecommerce.stock.service.StockService.sanitizeForCsv("@SUM(A1)"));
        assertEquals("\"say \"\"hi\"\"\"", com.ecommerce.stock.service.StockService.sanitizeForCsv("say \"hi\""));
        assertEquals("\"\"", com.ecommerce.stock.service.StockService.sanitizeForCsv(null));
    }

    @Test
    void internalEndpointsRequireTheExactServiceToken() throws Exception {
        // No SERVICE_TO_SERVICE_TOKEN in the test profile, so the code default applies.
        mockMvc.perform(get("/internal/stock/low-stock"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/stock/low-stock").header("X-Service-Token", "local-dev-service-token-change-mE"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/stock/low-stock").header("X-Service-Token", "local-dev-service-token-change-me"))
                .andExpect(status().isOk());
    }
}

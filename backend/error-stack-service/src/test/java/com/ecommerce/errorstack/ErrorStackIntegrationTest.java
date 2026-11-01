package com.ecommerce.errorstack;

import com.ecommerce.errorstack.dto.BreadcrumbDto;
import com.ecommerce.errorstack.dto.ErrorIngestRequest;
import com.ecommerce.errorstack.dto.TriageRequest;
import com.ecommerce.errorstack.model.ErrorGroup;
import com.ecommerce.errorstack.model.ErrorRecord;
import com.ecommerce.errorstack.model.ErrorSeverity;
import com.ecommerce.errorstack.model.ErrorStatus;
import com.ecommerce.errorstack.repository.ErrorGroupRepository;
import com.ecommerce.errorstack.repository.ErrorRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ErrorStackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ErrorGroupRepository groupRepository;

    @Autowired
    private ErrorRecordRepository recordRepository;

    @BeforeEach
    void setUp() {
        recordRepository.deleteAll();
        groupRepository.deleteAll();
    }

    @Test
    @DisplayName("Ingest: Successfully persists new error and creates error group")
    void testIngestError_createsGroupAndRecord() throws Exception {
        ErrorIngestRequest req = ErrorIngestRequest.builder()
                .serviceName("order-service")
                .environment("production")
                .version("1.0.0")
                .errorType("PaymentProcessingException")
                .message("Gateway connection timeout on port 443")
                .stackTrace("com.ecommerce.order.service.OrderService.processPayment(OrderService.java:142)\n\tat org.springframework.web.servlet...")
                .requestMethod("POST")
                .requestUri("/api/orders/checkout")
                .responseStatus(504)
                .traceId("trace-xyz-123")
                .severity(ErrorSeverity.CRITICAL)
                .breadcrumbs(List.of(
                        BreadcrumbDto.builder()
                                .category("ui.click")
                                .message("User clicked place order")
                                .timestamp(OffsetDateTime.now())
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/errors/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.fingerprint", notNullValue()))
                .andExpect(jsonPath("$.recordId", notNullValue()));

        assertEquals(1, groupRepository.count());
        assertEquals(1, recordRepository.count());

        ErrorGroup group = groupRepository.findAll().get(0);
        assertEquals("order-service", group.getServiceName());
        assertEquals("PaymentProcessingException", group.getErrorType());
        assertEquals(ErrorSeverity.CRITICAL, group.getSeverity());
        assertEquals(ErrorStatus.NEW, group.getStatus());
        assertEquals(1L, group.getOccurrencesCount());
    }

    @Test
    @DisplayName("Redaction: Strips JWT, passwords, and credit card numbers from stored errors")
    void testIngestError_redactsSensitiveData() throws Exception {
        ErrorIngestRequest req = ErrorIngestRequest.builder()
                .serviceName("user-service")
                .errorType("AuthenticationException")
                .message("Failed auth for user with password=SecretPassword123 and token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.abcdef1234567890")
                .stackTrace("com.ecommerce.user.AuthService.login(AuthService.java:55)\nFailed card 4111111111111111")
                .requestUri("/api/users/login?password=MyRawPassword&token=12345")
                .severity(ErrorSeverity.ERROR)
                .build();

        mockMvc.perform(post("/api/errors/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());

        ErrorRecord record = recordRepository.findAll().get(0);
        // Sensitive data must be redacted
        assertFalse(record.getMessage().contains("SecretPassword123"));
        assertFalse(record.getMessage().contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
        assertTrue(record.getMessage().contains("[REDACTED]"));

        assertFalse(record.getStackTrace().contains("4111111111111111"));
        assertTrue(record.getStackTrace().contains("[REDACTED_CREDIT_CARD]"));

        assertFalse(record.getRequestUri().contains("MyRawPassword"));
        assertTrue(record.getRequestUri().contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Grouping: Repeated occurrences with identical fingerprint group together")
    void testRepeatedErrors_groupTogetherAndIncrementCount() throws Exception {
        ErrorIngestRequest req1 = ErrorIngestRequest.builder()
                .serviceName("cart-service")
                .errorType("StockUnavailableException")
                .message("Item 42 is out of stock")
                .stackTrace("com.ecommerce.cart.service.CartService.addItem(CartService.java:88)")
                .severity(ErrorSeverity.WARNING)
                .build();

        ErrorIngestRequest req2 = ErrorIngestRequest.builder()
                .serviceName("cart-service")
                .errorType("StockUnavailableException")
                .message("Item 99 is out of stock")
                .stackTrace("com.ecommerce.cart.service.CartService.addItem(CartService.java:88)")
                .severity(ErrorSeverity.WARNING)
                .build();

        mockMvc.perform(post("/api/errors/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1))).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/errors/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2))).andExpect(status().isAccepted());

        // Should result in 1 group with 2 occurrences and 2 records
        assertEquals(1, groupRepository.count());
        assertEquals(2, recordRepository.count());

        ErrorGroup group = groupRepository.findAll().get(0);
        assertEquals(2L, group.getOccurrencesCount());
    }

    @Test
    @DisplayName("RBAC: Rejects access to Admin endpoints without Manager/Admin role")
    void testRbac_blocksUnauthorizedCustomers() throws Exception {
        mockMvc.perform(get("/api/errors/admin/overview")
                        .header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/errors/admin/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin Overview: Computes metrics across multiple services and severities")
    void testOverviewMetrics_aggregatesCorrectly() throws Exception {
        ErrorIngestRequest r1 = ErrorIngestRequest.builder()
                .serviceName("order-service")
                .errorType("TimeoutException")
                .message("Database connection pool timeout")
                .severity(ErrorSeverity.CRITICAL)
                .build();

        ErrorIngestRequest r2 = ErrorIngestRequest.builder()
                .serviceName("shell-app")
                .errorType("ChunkLoadError")
                .message("Loading chunk 3001 failed")
                .severity(ErrorSeverity.ERROR)
                .build();

        mockMvc.perform(post("/api/errors/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r1))).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/errors/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r2))).andExpect(status().isAccepted());

        mockMvc.perform(get("/api/errors/admin/overview")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGroups", is(2)))
                .andExpect(jsonPath("$.totalOccurrences", is(2)))
                .andExpect(jsonPath("$.newErrorsCount", is(2)))
                .andExpect(jsonPath("$.unresolvedCriticalCount", is(1)))
                .andExpect(jsonPath("$.serviceBreakdown.order-service", is(1)))
                .andExpect(jsonPath("$.serviceBreakdown.shell-app", is(1)));
    }

    @Test
    @DisplayName("Triage: Staff can update status to INVESTIGATING or RESOLVED and assign assignee")
    void testTriageGroup_updatesStatusAndAssignee() throws Exception {
        ErrorIngestRequest req = ErrorIngestRequest.builder()
                .serviceName("inventory-service")
                .errorType("OptimisticLockException")
                .message("Row was updated by another transaction")
                .severity(ErrorSeverity.ERROR)
                .build();

        mockMvc.perform(post("/api/errors/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isAccepted());

        ErrorGroup group = groupRepository.findAll().get(0);
        String fingerprint = group.getFingerprint();

        TriageRequest triage = TriageRequest.builder()
                .status(ErrorStatus.INVESTIGATING)
                .assignedTo("admin@ecommerce.com")
                .build();

        mockMvc.perform(patch("/api/errors/admin/groups/" + fingerprint + "/triage")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(triage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INVESTIGATING")))
                .andExpect(jsonPath("$.assignedTo", is("admin@ecommerce.com")));

        ErrorGroup updated = groupRepository.findByFingerprint(fingerprint).orElseThrow();
        assertEquals(ErrorStatus.INVESTIGATING, updated.getStatus());
        assertEquals("admin@ecommerce.com", updated.getAssignedTo());
    }
}

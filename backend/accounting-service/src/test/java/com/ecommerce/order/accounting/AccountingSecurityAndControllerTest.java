package com.ecommerce.order.accounting;

import com.ecommerce.order.accounting.dto.CreateRefundRequest;
import com.ecommerce.order.accounting.dto.ManualAdjustmentRequest;
import com.ecommerce.order.accounting.dto.ProcessRefundRequest;
import com.ecommerce.order.accounting.dto.ReversalRequest;
import com.ecommerce.order.accounting.model.EntryType;
import com.ecommerce.order.accounting.model.LedgerTransaction;
import com.ecommerce.order.accounting.model.TransactionStatus;
import com.ecommerce.order.accounting.model.TransactionType;
import com.ecommerce.order.accounting.repository.AccountingAuditLogRepository;
import com.ecommerce.order.accounting.repository.LedgerTransactionRepository;
import com.ecommerce.order.accounting.repository.RefundRequestRepository;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.repository.OrderRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AccountingSecurityAndControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LedgerTransactionRepository ledgerRepository;

    @Autowired
    private AccountingAuditLogRepository auditLogRepository;

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
        refundRequestRepository.deleteAll();
        ledgerRepository.deleteAll();
    }

    @Test
    @DisplayName("Customer Endpoint: Missing X-User-Id header should return 401 Unauthorized")
    void testMissingUserIdHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/accounting/balance"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/accounting/overview"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/accounting/ledger"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Customer Endpoint: Cross-customer isolation prevents viewing another customer's ledger entry")
    void testCrossCustomerAccessBlocked() throws Exception {
        // Create transaction owned by User #99
        LedgerTransaction tx = ledgerRepository.save(LedgerTransaction.builder()
                .userId(99L)
                .transactionType(TransactionType.ORDER_PAYMENT)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .status(TransactionStatus.POSTED)
                .description("Payment for Order #99")
                .referenceType("ORDER")
                .referenceId("99")
                .idempotencyKey("ORDER_PAYMENT_99")
                .createdBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .build());

        // User #42 attempts to access User #99's transaction details
        mockMvc.perform(get("/api/accounting/ledger/" + tx.getId())
                        .header("X-User-Id", 42L)
                        .header("X-User-Role", "ROLE_USER"))
                .andExpect(status().isForbidden());

        // User #99 (owner) accesses successfully
        mockMvc.perform(get("/api/accounting/ledger/" + tx.getId())
                        .header("X-User-Id", 99L)
                        .header("X-User-Role", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(tx.getId().intValue())))
                .andExpect(jsonPath("$.userId", is(99)))
                .andExpect(jsonPath("$.amount", is(75.00)));

        // Admin accesses successfully
        mockMvc.perform(get("/api/accounting/ledger/" + tx.getId())
                        .header("X-User-Id", 1L)
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(tx.getId().intValue())));
    }

    @Test
    @DisplayName("Customer Endpoint: Customer can fetch account overview and export CSV statement")
    void testCustomerOverviewAndStatementExport() throws Exception {
        // Create transaction for user 50
        ledgerRepository.save(LedgerTransaction.builder()
                .userId(50L)
                .transactionType(TransactionType.ORDER_PAYMENT)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("110.00"))
                .currency("USD")
                .status(TransactionStatus.POSTED)
                .description("Payment for Order #501")
                .referenceType("ORDER")
                .referenceId("501")
                .idempotencyKey("TEST_KEY_501")
                .createdBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .build());

        // Overview
        mockMvc.perform(get("/api/accounting/overview")
                        .header("X-User-Id", 50L)
                        .header("X-User-Role", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(50)))
                .andExpect(jsonPath("$.lifetimeSpend", is(110.0)))
                .andExpect(jsonPath("$.currency", is("USD")));

        // CSV Statement Export
        mockMvc.perform(get("/api/accounting/statements/export")
                        .header("X-User-Id", 50L)
                        .header("X-User-Role", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(content().string(containsString("Transaction ID")))
                .andExpect(content().string(containsString("ORDER_PAYMENT")));
    }

    @Test
    @DisplayName("Admin Endpoint: Standard user role should be rejected with 403 Forbidden")
    void testAdminEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/accounting/admin/ledger")
                        .header("Authorization", platformToken(10L, "ROLE_USER", "staff10@example.test")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/accounting/admin/adjustments")
                        .header("Authorization", platformToken(10L, "ROLE_USER", "staff10@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Role Separation: MANAGER role can read admin ledger and dashboard, but cannot post manual adjustments")
    void testManagerRoleReadAllowedWriteForbidden() throws Exception {
        // 1. MANAGER can read admin dashboard
        mockMvc.perform(get("/api/accounting/admin/dashboard")
                        .header("Authorization", platformToken(20L, "ROLE_MANAGER", "staff20@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disclaimer", containsString("subledger totals")));

        // 2. MANAGER can read admin ledger
        mockMvc.perform(get("/api/accounting/admin/ledger")
                        .header("Authorization", platformToken(20L, "ROLE_MANAGER", "staff20@example.test")))
                .andExpect(status().isOk());

        // 3. MANAGER is rejected with 403 when attempting to post manual adjustments
        ManualAdjustmentRequest request = ManualAdjustmentRequest.builder()
                .userId(15L)
                .transactionType(TransactionType.MANUAL_CREDIT)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .reason("Manager unauthorized test")
                .reference("REF-123")
                .build();

        mockMvc.perform(post("/api/accounting/admin/adjustments")
                        .header("Authorization", platformToken(20L, "ROLE_MANAGER", "staff20@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Customer Endpoint: Unknown ledger entry returns 404 rather than a server error")
    void testUnknownLedgerEntryReturns404() throws Exception {
        mockMvc.perform(get("/api/accounting/ledger/987654321")
                        .header("X-User-Id", 42L)
                        .header("X-User-Role", "ROLE_USER"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Admin Endpoint: Reversing an unknown transaction returns 404 rather than a server error")
    void testReverseUnknownTransactionReturns404() throws Exception {
        mockMvc.perform(post("/api/accounting/admin/reversals/987654321")
                        .header("Authorization", platformToken(1L, "ROLE_ADMIN", "admin@ecommerce.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer request\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Admin Endpoint: Adjustment without a reference is rejected with 400 rather than a server error")
    void testAdjustmentWithoutReferenceReturns400() throws Exception {
        ManualAdjustmentRequest request = ManualAdjustmentRequest.builder()
                .userId(15L)
                .transactionType(TransactionType.MANUAL_CREDIT)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .reason("Customer goodwill credit")
                .build();

        mockMvc.perform(post("/api/accounting/admin/adjustments")
                        .header("Authorization", platformToken(1L, "ROLE_ADMIN", "admin@ecommerce.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Reference is required for manual adjustments.")));
    }

    @Test
    @DisplayName("Internal Endpoint: Supplier payable with a wrong or missing service token returns 401")
    void testInternalAccountingRefusesWrongServiceToken() throws Exception {
        mockMvc.perform(post("/internal/accounting/supplier-payables")
                        .header("X-Service-Token", "wrong-service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/accounting/supplier-payables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin Endpoint: Admin role can post manual adjustment and view audit trail")
    void testAdminManualAdjustmentAndAuditLogs() throws Exception {
        ManualAdjustmentRequest request = ManualAdjustmentRequest.builder()
                .userId(15L)
                .transactionType(TransactionType.MANUAL_CREDIT)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .reason("Customer goodwill credit")
                .reference("TICKET-5521")
                .build();

        // 1. Post manual adjustment as ADMIN
        mockMvc.perform(post("/api/accounting/admin/adjustments")
                        .header("Authorization", platformToken(1L, "ROLE_ADMIN", "admin@ecommerce.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(15)))
                .andExpect(jsonPath("$.amount", is(50.00)))
                .andExpect(jsonPath("$.transactionType", is("MANUAL_CREDIT")))
                .andExpect(jsonPath("$.entryType", is("CREDIT")));

        // 2. Query audit logs as ADMIN
        mockMvc.perform(get("/api/accounting/admin/audit-logs")
                        .header("Authorization", platformToken(1L, "ROLE_ADMIN", "staff1@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].adminEmail", is("admin@ecommerce.com")))
                .andExpect(jsonPath("$.content[0].reason", is("Customer goodwill credit")));
    }

    static final String TEST_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    /** A platform (non-tenant) token, as issued by /api/users/login; the admin API now verifies it instead of trusting headers. */
    static String platformToken(long userId, String role, String email) {
        return "Bearer " + io.jsonwebtoken.Jwts.builder()
                .claim("id", userId).claim("role", role).claim("permissions", java.util.List.of())
                .setSubject(email).setExpiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes()), io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }
}

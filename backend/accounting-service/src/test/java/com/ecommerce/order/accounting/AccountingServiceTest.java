package com.ecommerce.order.accounting;

import com.ecommerce.order.accounting.dto.*;
import com.ecommerce.order.accounting.model.*;
import com.ecommerce.order.accounting.repository.*;
import com.ecommerce.order.accounting.service.AccountingService;
import com.ecommerce.order.OrderDataSyncService;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AccountingServiceTest {

    @Autowired
    private AccountingService accountingService;

    @Autowired
    private LedgerTransactionRepository ledgerRepository;

    @Autowired
    private AccountingAuditLogRepository auditLogRepository;

    @Autowired
    private RefundRequestRepository refundRequestRepository;

    @Autowired
    private CustomerAccountingPreferencesRepository preferencesRepository;

    @Autowired
    private CustomerPaymentMethodRepository paymentMethodRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private OrderDataSyncService orderDataSyncService;

    @BeforeEach
    void cleanUp() {
        // The order-service is not running in unit tests; serve snapshots from the local read model.
        org.mockito.Mockito.when(orderDataSyncService.refreshOrder(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> orderRepository.findById(inv.getArgument(0)).orElse(null));
        org.mockito.Mockito.when(orderDataSyncService.updateOrderStatus(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> orderRepository.findById(inv.<Long>getArgument(0)).map(o -> {
                    o.setStatus(inv.getArgument(1));
                    return orderRepository.save(o);
                }).orElse(null));
        auditLogRepository.deleteAll();
        refundRequestRepository.deleteAll();
        ledgerRepository.deleteAll();
        preferencesRepository.deleteAll();
        paymentMethodRepository.deleteAll();
    }

    @Test
    @DisplayName("Should idempotently record order payment and prevent duplicate entries")
    void testRecordOrderPaymentIdempotency() {
        Order order = new Order();
        order.setId(101L);
        order.setUserId(55L);
        order.setTotalAmount(new BigDecimal("149.99"));
        order.setStatus("PLACED");

        // First attempt - creates transaction
        LedgerTransaction tx1 = accountingService.recordOrderPayment(order);
        assertThat(tx1).isNotNull();
        assertThat(tx1.getId()).isNotNull();
        assertThat(tx1.getUserId()).isEqualTo(55L);
        assertThat(tx1.getAmount()).isEqualByComparingTo(new BigDecimal("149.99"));
        assertThat(tx1.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(tx1.getTransactionType()).isEqualTo(TransactionType.ORDER_PAYMENT);
        assertThat(tx1.getCurrency()).isEqualTo("USD");
        assertThat(tx1.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(tx1.getIdempotencyKey()).isEqualTo("ORDER_PAYMENT_101");

        // Second attempt with same order - must be idempotent and return existing entry without creating duplicate
        LedgerTransaction tx2 = accountingService.recordOrderPayment(order);
        assertThat(tx2.getId()).isEqualTo(tx1.getId());
        assertThat(ledgerRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should idempotently record order refund")
    void testRecordOrderRefundIdempotency() {
        Order order = new Order();
        order.setId(202L);
        order.setUserId(77L);
        order.setTotalAmount(new BigDecimal("89.50"));
        order.setStatus("CANCELLED");

        LedgerTransaction refund1 = accountingService.recordOrderRefund(order, "Customer requested cancellation");
        assertThat(refund1.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(refund1.getTransactionType()).isEqualTo(TransactionType.ORDER_REFUND);
        assertThat(refund1.getAmount()).isEqualByComparingTo(new BigDecimal("89.50"));
        assertThat(refund1.getCurrency()).isEqualTo("USD");

        // Retry refund call - idempotent
        LedgerTransaction refund2 = accountingService.recordOrderRefund(order, "Customer requested cancellation");
        assertThat(refund2.getId()).isEqualTo(refund1.getId());
        assertThat(ledgerRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should accurately calculate account balance with credits, debits, and explanatory note")
    void testBalanceCalculationWithExplanation() {
        Long userId = 99L;

        // 1. Payment (Debit: $100.00)
        Order order1 = new Order();
        order1.setId(301L);
        order1.setUserId(userId);
        order1.setTotalAmount(new BigDecimal("100.00"));
        accountingService.recordOrderPayment(order1);

        // 2. Second Payment (Debit: $50.00)
        Order order2 = new Order();
        order2.setId(302L);
        order2.setUserId(userId);
        order2.setTotalAmount(new BigDecimal("50.00"));
        accountingService.recordOrderPayment(order2);

        // 3. Order Refund (Credit: $50.00)
        accountingService.recordOrderRefund(order2, "Refund for order 302");

        // 4. Manual Store Credit (Credit: $20.00)
        ManualAdjustmentRequest creditReq = ManualAdjustmentRequest.builder()
                .userId(userId)
                .transactionType(TransactionType.MANUAL_CREDIT)
                .amount(new BigDecimal("20.00"))
                .currency("USD")
                .reason("Courtesy loyalty reward credit")
                .reference("TICKET-7788")
                .build();
        accountingService.createManualAdjustment(1L, "admin@ecommerce.com", creditReq);

        AccountBalanceDto balance = accountingService.getAccountBalance(userId);

        // Total Debits = 100.00 + 50.00 = 150.00
        // Total Credits = 50.00 + 20.00 = 70.00
        // Net Balance = Credits - Debits = 70.00 - 150.00 = -80.00
        assertThat(balance.getUserId()).isEqualTo(userId);
        assertThat(balance.getCurrency()).isEqualTo("USD");
        assertThat(balance.getTotalDebits()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(balance.getTotalCredits()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(balance.getNetBalance()).isEqualByComparingTo(new BigDecimal("-80.00"));
        assertThat(balance.getExplanation()).contains("Net Balance is calculated as (Total Credits + Refunds) minus (Total Payments + Debits)");
    }

    @Test
    @DisplayName("Should calculate detailed account overview KPIs and explanation notes")
    void testAccountOverviewCalculations() {
        Long userId = 120L;

        // Payment 1: $120.00
        Order o1 = new Order();
        o1.setId(601L);
        o1.setUserId(userId);
        o1.setTotalAmount(new BigDecimal("120.00"));
        accountingService.recordOrderPayment(o1);

        // Payment 2: $80.00
        Order o2 = new Order();
        o2.setId(602L);
        o2.setUserId(userId);
        o2.setTotalAmount(new BigDecimal("80.00"));
        accountingService.recordOrderPayment(o2);

        // Refund for o2: $80.00
        accountingService.recordOrderRefund(o2, "Full refund for returned goods");

        // Manual store credit: $15.00
        ManualAdjustmentRequest creditReq = ManualAdjustmentRequest.builder()
                .userId(userId)
                .transactionType(TransactionType.MANUAL_CREDIT)
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .reason("Store credit goodwill")
                .reference("PROMO-2026")
                .build();
        accountingService.createManualAdjustment(1L, "admin@ecommerce.com", creditReq);

        AccountOverviewDto overview = accountingService.getAccountOverview(userId);

        assertThat(overview.getUserId()).isEqualTo(userId);
        assertThat(overview.getLifetimeSpend()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(overview.getLifetimeRefunds()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(overview.getAvailableStoreCredit()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(overview.getLifetimeSpendExplanation()).isNotBlank();
        assertThat(overview.getStoreCreditExplanation()).isNotBlank();
    }

    @Test
    @DisplayName("Should generate itemized order invoice with line items, tax, shipping, and tokenized reference")
    void testOrderInvoiceGeneration() {
        Order order = new Order();
        order.setUserId(88L);
        order.setTotalAmount(new BigDecimal("215.00"));
        order.setStatus("DELIVERED");

        OrderItem item1 = new OrderItem();
        item1.setProductId(10L);
        item1.setProductName("Mechanical Keyboard");
        item1.setPrice(new BigDecimal("100.00"));
        item1.setQuantity(2);
        item1.setOrder(order);

        order.setItems(List.of(item1));
        Order savedOrder = orderRepository.save(order);

        // Record payment transaction
        accountingService.recordOrderPayment(savedOrder);

        OrderInvoiceDto invoice = accountingService.getOrderInvoice(savedOrder.getId(), 88L, "ROLE_USER");
        assertThat(invoice).isNotNull();
        assertThat(invoice.getOrderId()).isEqualTo(savedOrder.getId());
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(new BigDecimal("215.00"));
        assertThat(invoice.getItems()).hasSize(1);
        assertThat(invoice.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(invoice.getPaymentReference()).isNotBlank();
    }

    @Test
    @DisplayName("Should submit customer refund request and govern through approval to ledger credit")
    void testRefundRequestWorkflowApproval() {
        Order order = new Order();
        order.setUserId(44L);
        order.setTotalAmount(new BigDecimal("95.00"));
        order.setStatus("PLACED");
        Order savedOrder = orderRepository.save(order);
        accountingService.recordOrderPayment(savedOrder);

        // 1. Customer submits refund request
        CreateRefundRequest request = new CreateRefundRequest(savedOrder.getId(), new BigDecimal("95.00"), "Received damaged item");
        RefundRequestDto created = accountingService.submitRefundRequest(44L, "user44@ecommerce.com", request);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);

        // 2. Admin reviews and approves refund request
        ProcessRefundRequest approval = new ProcessRefundRequest(true, "Damage verified by support team");
        RefundRequestDto processed = accountingService.processRefundRequest(1L, "admin@ecommerce.com", created.getId(), approval);
        assertThat(processed.getStatus()).isEqualTo(RefundRequestStatus.PROCESSED);
        assertThat(processed.getDecisionReason()).isEqualTo("Damage verified by support team");

        // 3. Verify order status changed to REFUNDED
        Order updatedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo("REFUNDED");

        // 4. Verify linked ledger refund entry exists
        var ledgerEntries = ledgerRepository.findAll();
        boolean hasRefund = ledgerEntries.stream().anyMatch(e -> e.getTransactionType() == TransactionType.ORDER_REFUND);
        assertThat(hasRefund).isTrue();

        // 5. Verify audit log entry exists
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Should submit customer refund request and handle administrative rejection")
    void testRefundRequestWorkflowRejection() {
        Order order = new Order();
        order.setUserId(45L);
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setStatus("PLACED");
        Order savedOrder = orderRepository.save(order);
        accountingService.recordOrderPayment(savedOrder);

        CreateRefundRequest request = new CreateRefundRequest(savedOrder.getId(), new BigDecimal("50.00"), "Changed mind after delivery window");
        RefundRequestDto created = accountingService.submitRefundRequest(45L, "user45@ecommerce.com", request);

        ProcessRefundRequest rejection = new ProcessRefundRequest(false, "Return window has expired");
        RefundRequestDto processed = accountingService.processRefundRequest(1L, "admin@ecommerce.com", created.getId(), rejection);
        assertThat(processed.getStatus()).isEqualTo(RefundRequestStatus.REJECTED);

        // Order remains PLACED, no refund ledger transaction added
        Order updatedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo("PLACED");
    }

    @Test
    @DisplayName("Should update reconciliation status with notes and record audit trail")
    void testReconciliationStatusUpdate() {
        Order order = new Order();
        order.setId(777L);
        order.setUserId(12L);
        order.setTotalAmount(new BigDecimal("220.00"));
        order.setStatus("PLACED");
        LedgerTransaction tx = accountingService.recordOrderPayment(order);

        ReconciliationUpdateRequest updateReq = new ReconciliationUpdateRequest(ReconciliationStatus.FLAGGED_DISCREPANCY, "Gateway settlement discrepancy investigation");
        LedgerTransactionDto updated = accountingService.updateReconciliationStatus(1L, "admin@ecommerce.com", tx.getId(), updateReq);

        assertThat(updated.getReconciliationStatus()).isEqualTo("FLAGGED_DISCREPANCY");
        assertThat(updated.getReconciliationNotes()).isEqualTo("Gateway settlement discrepancy investigation");

        var auditLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction().contains("RECONCILIATION"))
                .toList();
        assertThat(auditLogs).isNotEmpty();
    }

    @Test
    @DisplayName("Should export customer account statement CSV and admin master ledger CSV with audit logging")
    void testStatementAndLedgerCsvExports() {
        Long userId = 80L;
        Order order = new Order();
        order.setId(880L);
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("75.00"));
        order.setStatus("PLACED");
        accountingService.recordOrderPayment(order);

        // Customer statement CSV
        byte[] customerCsvBytes = accountingService.exportCustomerStatementCsv(userId, null, null);
        String customerCsv = new String(customerCsvBytes, StandardCharsets.UTF_8);
        assertThat(customerCsv).contains("CUSTOMER ACCOUNT STATEMENT");
        assertThat(customerCsv).contains("ORDER_PAYMENT");
        assertThat(customerCsv).contains("75.00");

        // Admin master ledger CSV
        byte[] adminCsvBytes = accountingService.exportAdminLedgerCsv(1L, "admin@ecommerce.com", null, null, null, null, null);
        String adminCsv = new String(adminCsvBytes, StandardCharsets.UTF_8);
        assertThat(adminCsv).contains("MASTER ACCOUNTING SUBLEDGER EXPORT");
        assertThat(adminCsv).contains("ORDER_PAYMENT");

        // Admin export must produce an immutable audit log entry!
        var exportLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction().contains("LEDGER_EXPORT_CSV"))
                .toList();
        assertThat(exportLogs).isNotEmpty();
    }

    @Test
    @DisplayName("Should create auditable manual adjustment and register before/after audit log")
    void testManualAdjustmentWithAuditLogging() {
        ManualAdjustmentRequest request = ManualAdjustmentRequest.builder()
                .userId(12L)
                .transactionType(TransactionType.ADJUSTMENT)
                .amount(new BigDecimal("35.75"))
                .currency("USD")
                .reason("Billing reconciliation adjustment")
                .reference("REF-ADJ-9901")
                .build();

        LedgerTransactionDto created = accountingService.createManualAdjustment(1L, "admin@ecommerce.com", request);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getAmount()).isEqualByComparingTo(new BigDecimal("35.75"));
        assertThat(created.getCurrency()).isEqualTo("USD");
        assertThat(created.getCreatedBy()).isEqualTo("admin@ecommerce.com");

        // Verify Audit Log entry
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(1L);
        var auditLogs = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction().contains("MANUAL_ADJUSTMENT"))
                .toList();
        assertThat(auditLogs.get(0).getAdminEmail()).isEqualTo("admin@ecommerce.com");
        assertThat(auditLogs.get(0).getReason()).isEqualTo("Billing reconciliation adjustment");
        assertThat(auditLogs.get(0).getAfterValue()).contains("35.75");
    }

    @Test
    @DisplayName("Should create immutable reversing entry without deleting original transaction and prevent duplicate reversal")
    void testTransactionReversalBehavior() {
        // Step 1: Create an original payment transaction
        Order order = new Order();
        order.setId(401L);
        order.setUserId(25L);
        order.setTotalAmount(new BigDecimal("200.00"));
        LedgerTransaction original = accountingService.recordOrderPayment(order);

        // Step 2: Reverse the payment transaction
        ReversalRequest reversalReq = new ReversalRequest("Accidental double payment charge reversal");
        LedgerTransactionDto reversal = accountingService.reverseTransaction(
                1L, "admin@ecommerce.com", original.getId(), reversalReq);

        assertThat(reversal.getId()).isNotEqualTo(original.getId());
        assertThat(reversal.getTransactionType()).isEqualTo(TransactionType.REVERSAL);
        assertThat(reversal.getEntryType()).isEqualTo(EntryType.CREDIT); // Opposite of original DEBIT
        assertThat(reversal.getAmount()).isEqualByComparingTo(original.getAmount());
        assertThat(reversal.getReversalOfId()).isEqualTo(original.getId());
        assertThat(reversal.getReason()).isEqualTo("Accidental double payment charge reversal");

        // Verify original transaction remains immutable in database with status POSTED
        LedgerTransaction foundOriginal = ledgerRepository.findById(original.getId()).orElseThrow();
        assertThat(foundOriginal.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(ledgerRepository.count()).isEqualTo(2L); // Original + Reversal both exist

        // Net balance now cancels out mathematically to 0.00
        AccountBalanceDto balance = accountingService.getAccountBalance(25L);
        assertThat(balance.getNetBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        // Step 3: Attempting to reverse the same transaction again must fail safely
        assertThatThrownBy(() -> accountingService.reverseTransaction(1L, "admin@ecommerce.com", original.getId(), reversalReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has already been reversed");

        // Step 4: Attempting to reverse a reversal entry must fail safely
        assertThatThrownBy(() -> accountingService.reverseTransaction(1L, "admin@ecommerce.com", reversal.getId(), reversalReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot reverse a reversal transaction");
    }

    @Test
    @DisplayName("Should filter and paginate customer ledger transactions")
    void testPaginationAndFiltering() {
        Long userId = 33L;

        for (int i = 1; i <= 5; i++) {
            Order o = new Order();
            o.setId(500L + i);
            o.setUserId(userId);
            o.setTotalAmount(new BigDecimal("10.00").multiply(new BigDecimal(i)));
            accountingService.recordOrderPayment(o);
        }

        Page<LedgerTransactionDto> page1 = accountingService.getCustomerLedger(
                userId, null, null, TransactionType.ORDER_PAYMENT, TransactionStatus.POSTED, PageRequest.of(0, 3));

        assertThat(page1.getTotalElements()).isEqualTo(5L);
        assertThat(page1.getContent()).hasSize(3);
        assertThat(page1.getTotalPages()).isEqualTo(2);
    }
}

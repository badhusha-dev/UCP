package com.ecommerce.order.accounting.controller;

import com.ecommerce.order.accounting.dto.*;
import com.ecommerce.order.accounting.model.ReconciliationStatus;
import com.ecommerce.order.accounting.model.RefundRequestStatus;
import com.ecommerce.order.accounting.model.TransactionStatus;
import com.ecommerce.order.accounting.model.TransactionType;
import com.ecommerce.order.accounting.service.AccountingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/accounting/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Accounting & Audit", description = "Administrator endpoints for cross-customer ledgers, executive dashboards, manual adjustments, reversals, refund governance, and audit trails")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccountingController {

    private final AccountingService accountingService;

    @Operation(summary = "Accounting Executive Dashboard", description = "Summarizes gross sales, refunds, adjustments, net activity, and reconciliation discrepancies.")
    @GetMapping("/dashboard")
    public ResponseEntity<AccountingDashboardDto> getDashboard(
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "USD") String currency) {

        assertManagerOrAdmin(headerUserRole);
        return ResponseEntity.ok(accountingService.getAdminDashboard(startDate, endDate, currency));
    }

    @Operation(summary = "Search & Filter Cross-Customer Ledgers", description = "Allows administrators and managers to query transactions across all customers with filters.")
    @GetMapping("/ledger")
    public ResponseEntity<Page<LedgerTransactionDto>> getAdminLedger(
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String referenceId,
            @RequestParam(required = false) ReconciliationStatus reconciliationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        assertManagerOrAdmin(headerUserRole);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<LedgerTransactionDto> results = accountingService.getAdminLedger(
                userId, customerEmail, startDate, endDate, type, status, referenceId, reconciliationStatus, pageable);
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Get Customer Balance by ID", description = "Allows staff to inspect any customer's net balance, total credits, and debits.")
    @GetMapping("/balance/{userId}")
    public ResponseEntity<AccountBalanceDto> getCustomerBalance(
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @PathVariable Long userId) {

        assertManagerOrAdmin(headerUserRole);
        return ResponseEntity.ok(accountingService.getAccountBalance(userId));
    }

    @Operation(summary = "Record Manual Accounting Adjustment", description = "Creates an auditable manual credit, debit, or adjustment. Strict Admin access required.")
    @PostMapping("/adjustments")
    public ResponseEntity<LedgerTransactionDto> createAdjustment(
            @RequestHeader(value = "X-User-Id", required = false) Long headerAdminId,
            @RequestHeader(value = "X-User-Email", required = false) String headerAdminEmail,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @Valid @RequestBody ManualAdjustmentRequest request) {

        assertAdminRole(headerUserRole);

        LedgerTransactionDto created = accountingService.createManualAdjustment(
                headerAdminId, headerAdminEmail, request);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @Operation(summary = "Reverse a Posted Transaction", description = "Non-destructive transaction reversal with compensating entry and audit log.")
    @PostMapping("/reversals/{transactionId}")
    public ResponseEntity<LedgerTransactionDto> reverseTransaction(
            @RequestHeader(value = "X-User-Id", required = false) Long headerAdminId,
            @RequestHeader(value = "X-User-Email", required = false) String headerAdminEmail,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @PathVariable Long transactionId,
            @Valid @RequestBody ReversalRequest request) {

        assertAdminRole(headerUserRole);

        LedgerTransactionDto result = accountingService.reverseTransaction(
                headerAdminId, headerAdminEmail, transactionId, request);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Query Refund Requests", description = "Queries submitted refund requests across orders with filter options.")
    @GetMapping("/refunds")
    public ResponseEntity<Page<RefundRequestDto>> getRefundRequests(
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) RefundRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        assertManagerOrAdmin(headerUserRole);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(accountingService.getAdminRefundRequests(orderId, userId, status, pageable));
    }

    @Operation(summary = "Process Refund Request", description = "Approves or rejects a customer refund request. Strict Admin access required.")
    @PostMapping("/refunds/{id}/process")
    public ResponseEntity<RefundRequestDto> processRefund(
            @RequestHeader(value = "X-User-Id", required = false) Long headerAdminId,
            @RequestHeader(value = "X-User-Email", required = false) String headerAdminEmail,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @PathVariable Long id,
            @Valid @RequestBody ProcessRefundRequest request) {

        assertAdminRole(headerUserRole);
        RefundRequestDto processed = accountingService.processRefundRequest(headerAdminId, headerAdminEmail, id, request);
        return ResponseEntity.ok(processed);
    }

    @Operation(summary = "Update Reconciliation Status", description = "Flags or updates reconciliation review status on a ledger transaction.")
    @PutMapping("/reconciliation/{transactionId}")
    public ResponseEntity<LedgerTransactionDto> updateReconciliation(
            @RequestHeader(value = "X-User-Id", required = false) Long headerAdminId,
            @RequestHeader(value = "X-User-Email", required = false) String headerAdminEmail,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @PathVariable Long transactionId,
            @Valid @RequestBody ReconciliationUpdateRequest request) {

        assertAdminRole(headerUserRole);
        LedgerTransactionDto updated = accountingService.updateReconciliationStatus(
                headerAdminId, headerAdminEmail, transactionId, request);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Export Admin Master Ledger CSV", description = "Exports filtered cross-customer ledger data as CSV with mandatory audit logging.")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAdminLedger(
            @RequestHeader(value = "X-User-Id", required = false) Long headerAdminId,
            @RequestHeader(value = "X-User-Email", required = false) String headerAdminEmail,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status) {

        assertManagerOrAdmin(headerUserRole);
        byte[] csvBytes = accountingService.exportAdminLedgerCsv(
                headerAdminId, headerAdminEmail, userId, startDate, endDate, type, status);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"admin_ledger_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }

    @Operation(summary = "View Audit Trail Logs", description = "Retrieves immutable audit trail entries recording administrator identity, action, and before/after values.")
    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AccountingAuditLogDto>> getAuditLogs(
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        assertManagerOrAdmin(headerUserRole);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(accountingService.getAuditLogs(pageable));
    }

    private void assertAdminRole(String role) {
        if (role == null || (!role.equalsIgnoreCase("ADMIN") && !role.equalsIgnoreCase("ROLE_ADMIN"))) {
            log.warn("Security Alert: Unauthorized write attempt to Admin Accounting by role: {}", role);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Administrator privileges required for this action.");
        }
    }

    private void assertManagerOrAdmin(String role) {
        if (role == null || (!role.contains("ADMIN") && !role.contains("MANAGER"))) {
            log.warn("Security Alert: Unauthorized read attempt to Accounting Console by role: {}", role);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Staff authorization (Manager or Admin) required.");
        }
    }
}

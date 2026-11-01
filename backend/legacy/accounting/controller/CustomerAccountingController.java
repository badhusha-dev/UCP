package com.ecommerce.order.accounting.controller;

import com.ecommerce.order.accounting.dto.*;
import com.ecommerce.order.accounting.model.RefundRequestStatus;
import com.ecommerce.order.accounting.model.TransactionStatus;
import com.ecommerce.order.accounting.model.TransactionType;
import com.ecommerce.order.accounting.service.AccountingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import java.util.List;

@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customer Accounting", description = "Customer self-service endpoints for personal transaction ledgers, invoices, refunds, and balance")
@SecurityRequirement(name = "bearerAuth")
public class CustomerAccountingController {

    private final AccountingService accountingService;

    @Operation(summary = "Get Customer Account Balance", description = "Retrieves net account balance, total credits, and total debits with explanation.")
    @GetMapping("/balance")
    public ResponseEntity<AccountBalanceDto> getBalance(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        Long userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(accountingService.getAccountBalance(userId));
    }

    @Operation(summary = "Get Customer Account Overview", description = "Retrieves current balance, lifetime spend, lifetime refunds, pending transactions, and calculation formulas.")
    @GetMapping("/overview")
    public ResponseEntity<AccountOverviewDto> getOverview(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        Long userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(accountingService.getAccountOverview(userId));
    }

    @Operation(summary = "Get Customer Transaction Ledger", description = "Retrieves paginated personal transaction ledger filtered by date range, type, and status.")
    @GetMapping("/ledger")
    public ResponseEntity<Page<LedgerTransactionDto>> getLedger(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        Long userId = resolveUserId(headerUserId);
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<LedgerTransactionDto> results = accountingService.getCustomerLedger(userId, startDate, endDate, type, status, pageable);
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Get Ledger Entry Details", description = "Retrieves details of a specific ledger entry. Customers can only view their own transactions.")
    @GetMapping("/ledger/{id}")
    public ResponseEntity<LedgerTransactionDto> getEntryDetails(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole) {

        Long userId = resolveUserId(headerUserId);
        LedgerTransactionDto transaction = accountingService.getTransactionById(id);

        boolean isAdminOrManager = headerUserRole != null &&
                (headerUserRole.contains("ADMIN") || headerUserRole.contains("MANAGER"));

        if (!transaction.getUserId().equals(userId) && !isAdminOrManager) {
            log.warn("Security Alert: User #{} attempted unauthorized access to transaction #{} owned by User #{}",
                    userId, id, transaction.getUserId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not authorized to view another customer's accounting data.");
        }

        return ResponseEntity.ok(transaction);
    }

    @Operation(summary = "Get Order Invoice / Printable Receipt", description = "Retrieves itemized invoice with line items, totals, payment reference, and printable layout.")
    @GetMapping("/invoices/{orderId}")
    public ResponseEntity<OrderInvoiceDto> getOrderInvoice(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-User-Role", required = false) String headerUserRole) {

        Long userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(accountingService.getOrderInvoice(orderId, userId, headerUserRole));
    }

    @Operation(summary = "Submit Refund Request", description = "Customer requests a refund for a completed order.")
    @PostMapping("/refunds/request")
    public ResponseEntity<RefundRequestDto> submitRefundRequest(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "X-User-Email", required = false) String headerUserEmail,
            @Valid @RequestBody CreateRefundRequest request) {

        Long userId = resolveUserId(headerUserId);
        RefundRequestDto result = accountingService.submitRefundRequest(userId, headerUserEmail, request);
        return new ResponseEntity<>(result, HttpStatus.CREATED);
    }

    @Operation(summary = "Get Customer Refund Requests", description = "Lists refund requests submitted by the customer.")
    @GetMapping("/refunds")
    public ResponseEntity<Page<RefundRequestDto>> getRefundRequests(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(required = false) RefundRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = resolveUserId(headerUserId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(accountingService.getRefundRequests(userId, status, pageable));
    }

    @Operation(summary = "Get Saved Payment Methods", description = "Retrieves tokenized payment methods on file (last 4 digits only).")
    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        Long userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(accountingService.getPaymentMethods(userId));
    }

    @Operation(summary = "Get Accounting Preferences", description = "Retrieves customer email receipts and statement notification preferences.")
    @GetMapping("/preferences")
    public ResponseEntity<AccountingPreferencesDto> getPreferences(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId) {
        Long userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(accountingService.getPreferences(userId));
    }

    @Operation(summary = "Update Accounting Preferences", description = "Updates customer email receipts and statement notification preferences.")
    @PutMapping("/preferences")
    public ResponseEntity<AccountingPreferencesDto> updatePreferences(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestBody AccountingPreferencesDto dto) {
        Long userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(accountingService.updatePreferences(userId, dto));
    }

    @Operation(summary = "Export Customer Statement CSV", description = "Downloads date-range account statement as CSV with totals and balances.")
    @GetMapping("/statements/export")
    public ResponseEntity<byte[]> exportStatement(
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        Long userId = resolveUserId(headerUserId);
        byte[] csvBytes = accountingService.exportCustomerStatementCsv(userId, startDate, endDate);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"account_statement_" + userId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }

    private Long resolveUserId(Long headerUserId) {
        if (headerUserId == null || headerUserId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid user identity header.");
        }
        return headerUserId;
    }
}

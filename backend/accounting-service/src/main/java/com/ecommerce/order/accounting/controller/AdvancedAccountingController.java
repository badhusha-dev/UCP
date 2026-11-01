package com.ecommerce.order.accounting.controller;

import com.ecommerce.order.accounting.service.AdvancedAccountingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting/admin")
@RequiredArgsConstructor
public class AdvancedAccountingController {
    private final AdvancedAccountingService service;

    @GetMapping("/accounts")
    public List<Map<String, Object>> accounts(@RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions) {
        requireStaff(role, permissions);
        return service.accounts();
    }

    @PostMapping("/accounts")
    public ResponseEntity<Map<String, Object>> createAccount(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @Valid @RequestBody AccountRequest request) {
        requireStaff(role, permissions);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAccount(request.toMap(), actorId, actor));
    }

    @PutMapping("/accounts/{id}")
    public Map<String,Object> updateAccount(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id,
            @RequestBody Map<String,Object> request) {
        requireStaff(role, permissions);
        return service.updateAccount(id, request, actorId, actor);
    }

    @GetMapping("/journals")
    public Map<String, Object> journals(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        requireStaff(role, permissions);
        return service.journals(startDate, endDate, page, size);
    }

    @PostMapping("/journals")
    public ResponseEntity<Map<String, Object>> createJournal(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @RequestBody Map<String, Object> request) {
        requireStaff(role, permissions);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createJournal(request, actorId, actor));
    }

    @GetMapping("/journals/drafts")
    public Map<String, Object> journalDrafts(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestParam(required = false) String status) {
        requireStaff(role, permissions);
        return service.journalDrafts(status);
    }

    @PostMapping("/journals/drafts")
    public ResponseEntity<Map<String, Object>> createJournalDraft(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @RequestBody Map<String, Object> request) {
        requireStaff(role, permissions);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createJournalDraft(request, actorId, actor));
    }

    @PutMapping("/journals/drafts/{id}")
    public Map<String, Object> updateJournalDraft(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id,
            @RequestBody Map<String, Object> request) {
        requireStaff(role, permissions);
        return service.updateJournalDraft(id, request, actorId, actor);
    }

    @PostMapping("/journals/drafts/{id}/submit")
    public Map<String, Object> submitJournalDraft(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id) {
        requireStaff(role, permissions);
        return service.submitJournalDraft(id, actorId, actor);
    }

    @PostMapping("/journals/drafts/{id}/approve")
    public Map<String, Object> approveJournalDraft(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id) {
        requireStaff(role, permissions);
        return service.approveJournalDraft(id, actorId, actor);
    }

    @PostMapping("/journals/drafts/{id}/reject")
    public Map<String, Object> rejectJournalDraft(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id,
            @RequestBody Map<String, Object> request) {
        requireStaff(role, permissions);
        return service.rejectJournalDraft(id, String.valueOf(request.getOrDefault("reason", "")), actorId, actor);
    }

    @PostMapping("/journals/{id}/reverse")
    public Map<String, Object> reverseJournal(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> request) {
        requireStaff(role, permissions);
        return service.reverseJournal(id, request == null ? "" : String.valueOf(request.getOrDefault("reason", "")), actorId, actor);
    }

    @GetMapping("/invoices")
    public Map<String, Object> invoices(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        requireStaff(role, permissions);
        return service.invoices(status, type, term, page, size);
    }

    @GetMapping("/invoices/{id}")
    public Map<String, Object> invoice(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @PathVariable long id) {
        requireStaff(role, permissions);
        return service.invoice(id);
    }

    @PostMapping("/invoices")
    public ResponseEntity<Map<String, Object>> createInvoice(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @RequestBody Map<String, Object> request) {
        requireStaff(role, permissions);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createInvoice(request, actorId, actor));
    }

    @PutMapping("/invoices/{id}")
    public Map<String, Object> updateInvoice(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id,
            @RequestBody Map<String, Object> request) {
        requireStaff(role, permissions);
        return service.updateInvoice(id, request, actorId, actor);
    }

    @PatchMapping("/invoices/{id}/status")
    public Map<String, Object> updateInvoiceStatus(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id,
            @RequestBody Map<String, Object> request) {
        requireStaff(role, permissions);
        return service.updateInvoiceStatus(id, String.valueOf(request.get("status")), actorId, actor);
    }

    @GetMapping("/reports/profit-loss")
    public Map<String, Object> profitLoss(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "USD") String currency) {
        requireStaff(role, permissions);
        return service.profitLoss(startDate, endDate, currency);
    }

    @GetMapping("/reports/balance-sheet")
    public Map<String, Object> balanceSheet(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "USD") String currency) {
        requireStaff(role, permissions);
        return service.balanceSheet(asOfDate, currency);
    }

    @GetMapping("/reports/tax-summary")
    public Map<String, Object> taxSummary(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "USD") String currency) {
        requireStaff(role, permissions);
        return service.taxSummary(startDate, endDate, currency);
    }

    @GetMapping("/reports/cash-flow")
    public Map<String, Object> cashFlow(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "USD") String currency) {
        requireStaff(role, permissions);
        return service.cashFlow(startDate, endDate, currency);
    }

    @GetMapping("/reports/trends")
    public List<Map<String, Object>> revenueTrends(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "USD") String currency) {
        requireStaff(role, permissions);
        return service.revenueTrends(startDate, endDate, currency);
    }

    @GetMapping("/periods")
    public List<Map<String,Object>> periods(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions) {
        requireStaff(role, permissions);
        return service.accountingPeriods();
    }

    @PostMapping("/periods")
    public ResponseEntity<Map<String,Object>> createPeriod(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @RequestBody Map<String,Object> request) {
        requireStaff(role, permissions);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAccountingPeriod(request, actorId, actor));
    }

    @PostMapping("/periods/{id}/close")
    public Map<String,Object> closePeriod(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id, @RequestBody Map<String,Object> request) {
        requireStaff(role, permissions);
        return service.closeAccountingPeriod(id, String.valueOf(request.getOrDefault("reason", "")), actorId, actor);
    }

    @PostMapping("/periods/{id}/reopen")
    public Map<String,Object> reopenPeriod(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @PathVariable long id, @RequestBody Map<String,Object> request) {
        requireStaff(role, permissions);
        return service.reopenAccountingPeriod(id, String.valueOf(request.getOrDefault("reason", "")), actorId, actor);
    }

    @GetMapping(value = "/export/ledger", produces = "text/csv")
    public ResponseEntity<byte[]> exportLedger(@RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
                                                @RequestHeader(value = "X-User-Id", required = false) Long actorId,
                                                @RequestHeader(value = "X-User-Email", required = false) String actor) {
        requireStaff(role, permissions);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"accounting_ledger.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(service.exportJournalCsv(actorId, actor));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importCsv(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Permissions", required = false) String permissions,
            @RequestHeader(value = "X-User-Id", required = false) Long actorId,
            @RequestHeader(value = "X-User-Email", required = false) String actor,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        requireStaff(role, permissions);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.importCsv(file, type, actorId, actor));
    }

    private void requireStaff(String role, String permissions) {
        boolean roleStaff = role != null && (role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("ROLE_ADMIN")
                || role.equalsIgnoreCase("SUPER_ADMIN") || role.equalsIgnoreCase("MANAGER") || role.equalsIgnoreCase("ROLE_MANAGER"));
        boolean accountingGrant = permissions != null && java.util.Arrays.stream(permissions.split(","))
                .map(String::trim).anyMatch(permission -> permission.equals("*") || permission.startsWith("accounting:"));
        if (!roleStaff && !accountingGrant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accounting permission required.");
        }
    }

    public record AccountRequest(@NotBlank String code, @NotBlank String name, @NotBlank String accountType,
                                 @NotBlank String normalBalance, String parentCode, String description,
                                 Boolean cashAccount) {
        Map<String, Object> toMap() {
            return Map.of("code", code, "name", name, "accountType", accountType,
                    "normalBalance", normalBalance, "parentCode", parentCode == null ? "" : parentCode,
                    "description", description == null ? "" : description,
                    "cashAccount", cashAccount == null ? Boolean.FALSE : cashAccount);
        }
    }
}

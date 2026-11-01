package com.ecommerce.order.accounting.service;

import com.ecommerce.order.accounting.security.AccountingTenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplierPayablesService {
    private final JdbcTemplate jdbc;

    @Transactional
    public Map<String, Object> recordSupplierInvoice(Map<String, Object> request) {
        String idempotencyKey = required(request, "idempotencyKey");
        Long existingId = jdbc.query("SELECT id FROM journal_entries WHERE idempotency_key=? AND tenant_id=?", (rs, row) -> rs.getLong(1), idempotencyKey, AccountingTenantContext.current())
                .stream().findFirst().orElse(null);
        if (existingId != null) return entry(existingId);

        String invoiceNumber = required(request, "invoiceNumber");
        String supplierName = required(request, "supplierName");
        String poNumber = required(request, "purchaseOrderNumber");
        String currency = String.valueOf(request.getOrDefault("currency", "USD")).toUpperCase();
        if (!currency.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter code.");
        BigDecimal subtotal = amount(request.get("subtotal"));
        BigDecimal tax = amount(request.get("taxAmount"));
        BigDecimal total = amount(request.get("totalAmount"));
        if (subtotal.signum() < 0 || tax.signum() < 0 || total.signum() <= 0 || subtotal.add(tax).compareTo(total) != 0)
            badRequest("Payable totals must be non-negative and subtotal plus tax must equal total.");

        ensureAccount("PROC-INV", "Inventory Asset", "ASSET", "DEBIT");
        ensureAccount("PROC-TAX", "Recoverable Input Tax", "ASSET", "DEBIT");
        ensureAccount("PROC-AP", "Accounts Payable", "LIABILITY", "CREDIT");
        LocalDate periodDate = parseDate(request.get("issueDate"));
        String entryNumber = "AP-" + hash(idempotencyKey).substring(0, 22).toUpperCase();
        Long entryId;
        try {
            entryId = jdbc.queryForObject("INSERT INTO journal_entries(entry_number,description,reference_type,reference_id,period_date,posted_by,idempotency_key) VALUES(?,?,?,?,?,?,?) RETURNING id",
                    Long.class, entryNumber, "Supplier invoice " + invoiceNumber + " from " + supplierName + " for " + poNumber,
                    "SUPPLIER_INVOICE", invoiceNumber, periodDate, request.getOrDefault("postedBy", "procurement-service"), idempotencyKey);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            existingId = jdbc.query("SELECT id FROM journal_entries WHERE idempotency_key=? AND tenant_id=?", (rs, row) -> rs.getLong(1), idempotencyKey, AccountingTenantContext.current()).stream().findFirst().orElse(null);
            if (existingId != null) return entry(existingId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accounting reference already exists.");
        }
        if (subtotal.signum() > 0) insertLine(entryId, "PROC-INV", subtotal, BigDecimal.ZERO, "Received goods for " + poNumber, currency);
        if (tax.signum() > 0) insertLine(entryId, "PROC-TAX", tax, BigDecimal.ZERO, "Recoverable tax for " + invoiceNumber, currency);
        insertLine(entryId, "PROC-AP", BigDecimal.ZERO, total, "Payable to " + supplierName, currency);
        return entry(entryId);
    }

    /** Supplier return credit note: reverses payable (debit AP) against inventory asset and recoverable tax. Idempotent on idempotencyKey. */
    @Transactional
    public Map<String, Object> recordSupplierCreditNote(Map<String, Object> request) {
        String idempotencyKey = required(request, "idempotencyKey");
        Long existingId = jdbc.query("SELECT id FROM journal_entries WHERE idempotency_key=? AND tenant_id=?", (rs, row) -> rs.getLong(1), idempotencyKey, AccountingTenantContext.current()).stream().findFirst().orElse(null);
        if (existingId != null) return entry(existingId);
        String creditNumber = required(request, "creditNoteNumber");
        String supplierName = required(request, "supplierName");
        String currency = String.valueOf(request.getOrDefault("currency", "USD")).toUpperCase();
        if (!currency.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter code.");
        BigDecimal subtotal = amount(request.get("subtotal"));
        BigDecimal tax = amount(request.getOrDefault("taxAmount", "0"));
        BigDecimal total = amount(request.get("totalAmount"));
        if (subtotal.signum() < 0 || tax.signum() < 0 || total.signum() <= 0 || subtotal.add(tax).compareTo(total) != 0)
            badRequest("Credit totals must be non-negative and subtotal plus tax must equal total.");
        ensureAccount("PROC-INV", "Inventory Asset", "ASSET", "DEBIT");
        ensureAccount("PROC-TAX", "Recoverable Input Tax", "ASSET", "DEBIT");
        ensureAccount("PROC-AP", "Accounts Payable", "LIABILITY", "CREDIT");
        String entryNumber = "APC-" + hash(idempotencyKey).substring(0, 21).toUpperCase();
        Long entryId;
        try {
            entryId = jdbc.queryForObject("INSERT INTO journal_entries(entry_number,description,reference_type,reference_id,period_date,posted_by,idempotency_key) VALUES(?,?,?,?,?,?,?) RETURNING id",
                    Long.class, entryNumber, "Supplier credit note " + creditNumber + " from " + supplierName, "SUPPLIER_CREDIT_NOTE", creditNumber,
                    parseDate(request.get("issueDate")), request.getOrDefault("postedBy", "procurement-service"), idempotencyKey);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            existingId = jdbc.query("SELECT id FROM journal_entries WHERE idempotency_key=? AND tenant_id=?", (rs, row) -> rs.getLong(1), idempotencyKey, AccountingTenantContext.current()).stream().findFirst().orElse(null);
            if (existingId != null) return entry(existingId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accounting reference already exists.");
        }
        insertLine(entryId, "PROC-AP", total, BigDecimal.ZERO, "Credit from " + supplierName, currency);
        if (subtotal.signum() > 0) insertLine(entryId, "PROC-INV", BigDecimal.ZERO, subtotal, "Goods returned (" + creditNumber + ")", currency);
        if (tax.signum() > 0) insertLine(entryId, "PROC-TAX", BigDecimal.ZERO, tax, "Tax reversed (" + creditNumber + ")", currency);
        return entry(entryId);
    }

    private void ensureAccount(String code, String name, String type, String normalBalance) {
        jdbc.update("INSERT INTO accounts(code,name,account_type,normal_balance,description) VALUES(?,?,?,?,?) ON CONFLICT(tenant_id, code) DO NOTHING",
                code, name, type, normalBalance, "System account used by Supplier & Procurement Management.");
        Map<String, Object> configured = jdbc.queryForMap("SELECT account_type AS type,normal_balance AS \"normalBalance\" FROM accounts WHERE code=? AND tenant_id=?", code, AccountingTenantContext.current());
        if (!type.equals(configured.get("type")) || !normalBalance.equals(configured.get("normalBalance"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accounting account " + code + " has an incompatible chart-of-accounts configuration.");
        }
    }

    private void insertLine(Long entryId, String accountCode, BigDecimal debit, BigDecimal credit, String description, String currency) {
        jdbc.update("INSERT INTO journal_lines(entry_id,account_code,debit_amount,credit_amount,description,currency) VALUES(?,?,?,?,?,?)",
                entryId, accountCode, debit, credit, description, currency);
    }

    private Map<String, Object> entry(long id) {
        return jdbc.queryForMap("SELECT id AS \"entryId\",entry_number AS \"entryNumber\",reference_type AS \"referenceType\",reference_id AS \"referenceId\",period_date AS \"periodDate\",'POSTED' AS status FROM journal_entries WHERE id=? AND tenant_id=?", id, AccountingTenantContext.current());
    }

    private String required(Map<String, Object> request, String key) {
        String value = String.valueOf(request.getOrDefault(key, "")).trim();
        if (value.isBlank()) badRequest(key + " is required.");
        return value;
    }

    private BigDecimal amount(Object value) {
        try { return new BigDecimal(String.valueOf(value)).setScale(4, RoundingMode.HALF_UP); }
        catch (Exception exception) { badRequest("Supplier invoice amounts must be valid decimal values."); return BigDecimal.ZERO; }
    }

    private LocalDate parseDate(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return LocalDate.now();
        try { return value instanceof LocalDate date ? date : LocalDate.parse(String.valueOf(value).substring(0, 10)); }
        catch (Exception exception) { badRequest("Invoice issue date must use YYYY-MM-DD."); return LocalDate.now(); }
    }

    private String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("Unable to derive accounting reference", exception); }
    }

    private void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}

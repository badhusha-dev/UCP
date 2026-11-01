package com.ecommerce.order.accounting.service;

import com.ecommerce.order.accounting.security.AccountingTenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdvancedAccountingService {
    private static final Set<String> ACCOUNT_TYPES = Set.of("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE");
    private static final Set<String> BALANCES = Set.of("DEBIT", "CREDIT");
    private static final Set<String> INVOICE_STATES = Set.of("DRAFT", "ISSUED", "PAID", "OVERDUE", "VOID");
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> accounts() {
        return jdbc.queryForList("SELECT id, code, name, account_type AS \"accountType\", normal_balance AS \"normalBalance\", parent_code AS \"parentCode\", description, is_cash_account AS \"cashAccount\", is_active AS active, created_at AS \"createdAt\" FROM accounts WHERE tenant_id=? ORDER BY code", t());
    }

    private Map<String,Object> account(long id) {
        return jdbc.query("SELECT id, code, name, account_type AS \"accountType\", normal_balance AS \"normalBalance\", parent_code AS \"parentCode\", description, is_cash_account AS \"cashAccount\", is_active AS active, created_at AS \"createdAt\" FROM accounts WHERE id=? AND tenant_id=?",
                (rs, row) -> { Map<String,Object> m=new LinkedHashMap<>(); m.put("id",rs.getLong("id"));m.put("code",rs.getString("code"));m.put("name",rs.getString("name"));m.put("accountType",rs.getString("accountType"));m.put("normalBalance",rs.getString("normalBalance"));m.put("parentCode",rs.getString("parentCode"));m.put("description",rs.getString("description"));m.put("cashAccount",rs.getBoolean("cashAccount"));m.put("active",rs.getBoolean("active"));m.put("createdAt",rs.getTimestamp("createdAt"));return m; }, id, t())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    }

    @Transactional
    public Map<String, Object> createAccount(Map<String, Object> request, Long actorId, String actor) {
        String code = required(request, "code").toUpperCase(Locale.ROOT);
        String name = required(request, "name");
        String type = required(request, "accountType").toUpperCase(Locale.ROOT);
        String balance = required(request, "normalBalance").toUpperCase(Locale.ROOT);
        if (!ACCOUNT_TYPES.contains(type) || !BALANCES.contains(balance)) badRequest("Unsupported account type or normal balance.");
        String parent = text(request.get("parentCode"));
        if (!parent.isBlank() && !exists("SELECT EXISTS (SELECT 1 FROM accounts WHERE code = ? AND tenant_id = ?)", parent, t())) badRequest("Parent account does not exist.");
        long id = jdbc.queryForObject("INSERT INTO accounts(tenant_id,code,name,account_type,normal_balance,parent_code,description,is_cash_account) VALUES(?,?,?,?,?,NULLIF(?,''),?,?) RETURNING id",
                Long.class, t(), code, name, type, balance, parent, text(request.get("description")), Boolean.TRUE.equals(request.get("cashAccount")));
        Map<String,Object> saved=jdbc.queryForMap("SELECT id, code, name, account_type AS \"accountType\", normal_balance AS \"normalBalance\", parent_code AS \"parentCode\", description, is_cash_account AS \"cashAccount\", is_active AS active, created_at AS \"createdAt\" FROM accounts WHERE id = ? AND tenant_id = ?", id, t());
        recordAudit("ACCOUNT",id,"ACCOUNT_CREATE",actorId,actor,null,saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> updateAccount(long id, Map<String, Object> request, Long actorId, String actor) {
        Map<String,Object> before = account(id);
        String name = required(request, "name");
        String type = required(request, "accountType").toUpperCase(Locale.ROOT);
        String balance = required(request, "normalBalance").toUpperCase(Locale.ROOT);
        String parent = text(request.get("parentCode")).toUpperCase(Locale.ROOT);
        if (!ACCOUNT_TYPES.contains(type) || !BALANCES.contains(balance)) badRequest("Unsupported account type or normal balance.");
        if ((!type.equals(before.get("accountType")) || !balance.equals(before.get("normalBalance")))
                && exists("SELECT EXISTS (SELECT 1 FROM journal_lines WHERE account_code=? AND tenant_id=?)", String.valueOf(before.get("code")), t())) {
            badRequest("Account type and normal balance cannot change after the account has journal activity.");
        }
        if (!parent.isBlank() && !exists("SELECT EXISTS (SELECT 1 FROM accounts WHERE code = ? AND tenant_id = ?)", parent, t())) badRequest("Parent account does not exist.");
        if (parent.equalsIgnoreCase(String.valueOf(before.get("code"))) || (!parent.isBlank() && exists("WITH RECURSIVE descendants(code) AS (SELECT code FROM accounts WHERE parent_code=? AND tenant_id=? UNION ALL SELECT a.code FROM accounts a JOIN descendants d ON a.parent_code=d.code AND a.tenant_id=?) SELECT EXISTS(SELECT 1 FROM descendants WHERE code=?)", String.valueOf(before.get("code")), t(), t(), parent))) badRequest("An account cannot be nested beneath itself or one of its descendants.");
        boolean active = request.get("active") == null || Boolean.TRUE.equals(request.get("active"));
        jdbc.update("UPDATE accounts SET name=?,account_type=?,normal_balance=?,parent_code=NULLIF(?,''),description=?,is_cash_account=?,is_active=? WHERE id=? AND tenant_id=?",
                name, type, balance, parent, text(request.get("description")), Boolean.TRUE.equals(request.get("cashAccount")), active, id, t());
        Map<String,Object> after = account(id);
        recordAudit("ACCOUNT", id, "ACCOUNT_UPDATE", actorId, actor, before, after);
        return after;
    }

    public Map<String, Object> journals(LocalDate start, LocalDate end, int page, int size) {
        page = boundedPage(page); size = boundedSize(size);
        if (start != null && end != null && end.isBefore(start)) badRequest("endDate must be on or after startDate.");
        String where = " WHERE tenant_id=? AND (CAST(? AS DATE) IS NULL OR period_date >= ?) AND (CAST(? AS DATE) IS NULL OR period_date <= ?)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries" + where, Long.class, t(), start, start, end, end);
        List<Map<String, Object>> entries = jdbc.query("SELECT id, entry_number AS \"entryNumber\", description, reference_type AS \"referenceType\", reference_id AS \"referenceId\", period_date AS \"periodDate\", posted_by AS \"postedBy\", idempotency_key AS \"idempotencyKey\", is_reversal AS reversal, reversal_of_id AS \"reversalOfId\", created_at AS \"createdAt\" FROM journal_entries" + where + " ORDER BY period_date DESC, id DESC LIMIT ? OFFSET ?",
                (rs, n) -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", rs.getLong("id")); entry.put("entryNumber", rs.getString("entryNumber"));
                    entry.put("description", rs.getString("description")); entry.put("referenceType", rs.getString("referenceType"));
                    entry.put("referenceId", rs.getString("referenceId")); entry.put("periodDate", rs.getObject("periodDate"));
                    entry.put("postedBy", rs.getString("postedBy")); entry.put("idempotencyKey", rs.getString("idempotencyKey"));
                    entry.put("reversal", rs.getBoolean("reversal")); entry.put("reversalOfId", rs.getObject("reversalOfId"));
                    entry.put("createdAt", rs.getTimestamp("createdAt")); entry.put("lines", journalLines(rs.getLong("id")));
                    return entry;
                }, t(), start, start, end, end, size, page * size);
        return pageResult(entries, page, size, total == null ? 0 : total);
    }

    public List<Map<String,Object>> accountingPeriods() {
        return jdbc.queryForList("SELECT id,period_name AS \"periodName\",start_date AS \"startDate\",end_date AS \"endDate\",status,closed_at AS \"closedAt\",closed_by AS \"closedBy\",close_reason AS \"closeReason\",created_by AS \"createdBy\",created_at AS \"createdAt\" FROM accounting_periods WHERE tenant_id=? ORDER BY start_date DESC,id DESC", t());
    }

    @Transactional
    public Map<String,Object> createAccountingPeriod(Map<String,Object> request, Long actorId, String actor) {
        String name = required(request, "periodName");
        LocalDate start = parseDate(request.get("startDate"), null);
        LocalDate end = parseDate(request.get("endDate"), null);
        validatePeriod(start, end);
        jdbc.execute("SELECT pg_advisory_xact_lock(217, 17)");
        if (exists("SELECT EXISTS(SELECT 1 FROM accounting_periods WHERE tenant_id=? AND lower(period_name)=lower(?))", t(), name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An accounting period with this name already exists.");
        }
        if (exists("SELECT EXISTS(SELECT 1 FROM accounting_periods WHERE tenant_id=? AND start_date <= ? AND end_date >= ?)", t(), end, start)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accounting periods cannot overlap an existing period.");
        }
        Long id = jdbc.queryForObject("INSERT INTO accounting_periods(tenant_id,period_name,start_date,end_date,created_by) VALUES(?,?,?,?,?) RETURNING id",
                Long.class, t(), name, start, end, actor);
        Map<String,Object> saved = accountingPeriod(Objects.requireNonNull(id));
        recordAudit("ACCOUNTING_PERIOD", id, "PERIOD_CREATE", actorId, actor, null, saved);
        return saved;
    }

    @Transactional
    public Map<String,Object> closeAccountingPeriod(long id, String reason, Long actorId, String actor) {
        Map<String,Object> period = accountingPeriod(id);
        if ("CLOSED".equals(period.get("status"))) badRequest("This accounting period is already closed.");
        LocalDate end = parseDate(period.get("endDate"), null);
        if (end.isAfter(LocalDate.now())) badRequest("A future accounting period cannot be closed.");
        String closeReason = reason == null ? "" : reason.trim();
        if (closeReason.length() < 8) badRequest("Add a close note with at least 8 characters.");
        Long unbalanced = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT je.id FROM journal_entries je JOIN journal_lines jl ON jl.entry_id=je.id WHERE je.tenant_id=? AND je.period_date BETWEEN ? AND ? GROUP BY je.id HAVING SUM(jl.debit_amount) <> SUM(jl.credit_amount)) entries",
                Long.class, t(), period.get("startDate"), period.get("endDate"));
        if (unbalanced != null && unbalanced > 0) badRequest("This period has unbalanced journal entries and cannot be closed.");
        int changed = jdbc.update("UPDATE accounting_periods SET status='CLOSED',closed_at=NOW(),closed_by=?,close_reason=? WHERE id=? AND tenant_id=? AND status='OPEN'",
                actor == null || actor.isBlank() ? "system" : actor, closeReason, id, t());
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "The accounting period changed while it was being closed. Refresh and try again.");
        Map<String,Object> saved = accountingPeriod(id);
        recordAudit("ACCOUNTING_PERIOD", id, "PERIOD_CLOSE", actorId, actor, period, saved);
        return saved;
    }

    @Transactional
    public Map<String,Object> reopenAccountingPeriod(long id, String reason, Long actorId, String actor) {
        Map<String,Object> period = accountingPeriod(id);
        if (!"CLOSED".equals(period.get("status"))) badRequest("Only a closed period can be reopened.");
        String reopenReason = reason == null ? "" : reason.trim();
        if (reopenReason.length() < 10) badRequest("A reason of at least 10 characters is required to reopen a period.");
        int changed = jdbc.update("UPDATE accounting_periods SET status='OPEN',closed_at=NULL,closed_by=NULL,close_reason=? WHERE id=? AND tenant_id=? AND status='CLOSED'",
                "Reopened: " + reopenReason, id, t());
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "The accounting period changed while it was being reopened. Refresh and try again.");
        Map<String,Object> saved = accountingPeriod(id);
        recordAudit("ACCOUNTING_PERIOD", id, "PERIOD_REOPEN", actorId, actor, period, saved);
        return saved;
    }

    private Map<String,Object> accountingPeriod(long id) {
        return jdbc.query("SELECT id,period_name AS \"periodName\",start_date AS \"startDate\",end_date AS \"endDate\",status,closed_at AS \"closedAt\",closed_by AS \"closedBy\",close_reason AS \"closeReason\",created_by AS \"createdBy\",created_at AS \"createdAt\" FROM accounting_periods WHERE id=? AND tenant_id=?",
                mapRow(), id, t()).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Accounting period not found."));
    }

    private void ensureAccountingDateOpen(LocalDate date) {
        if (exists("SELECT EXISTS(SELECT 1 FROM accounting_periods WHERE tenant_id=? AND status='CLOSED' AND ? BETWEEN start_date AND end_date)", t(), date)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The accounting date falls within a closed period.");
        }
    }

    @Transactional
    public Map<String, Object> createJournal(Map<String, Object> request, Long actorId, String actor) {
        String key = required(request, "idempotencyKey");
        List<Map<String, Object>> lines = mapList(request.get("lines"));
        if (lines.size() < 2) badRequest("A journal needs at least two lines.");
        LocalDate date = parseDate(request.get("periodDate"), LocalDate.now());
        String currency = text(request.getOrDefault("currency", "USD")).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter ISO code.");
        BigDecimal debits = BigDecimal.ZERO, credits = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            String accountCode = required(line, "accountCode").toUpperCase(Locale.ROOT);
            if (!exists("SELECT EXISTS (SELECT 1 FROM accounts WHERE code=? AND is_active=TRUE AND tenant_id=?)", accountCode, t())) badRequest("Unknown or inactive account: " + accountCode);
            BigDecimal debit = money(line.get("debitAmount")); BigDecimal credit = money(line.get("creditAmount"));
            if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() == 0) == (credit.signum() == 0)) badRequest("Each journal line must have exactly one positive debit or credit.");
            if (line.containsKey("currency") && !currency.equalsIgnoreCase(text(line.get("currency")))) badRequest("A journal entry must use one currency.");
            debits = debits.add(debit); credits = credits.add(credit);
        }
        if (debits.compareTo(credits) != 0) badRequest("Journal entry is not balanced.");
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM journal_entries WHERE idempotency_key=? AND tenant_id=?", key, t());
        if (!existing.isEmpty()) return journal(((Number) existing.get(0).get("id")).longValue());
        ensureAccountingDateOpen(date);
        String postedBy = actor == null || actor.isBlank() ? "system" : actor;
        String number = "JRN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        Long id = jdbc.queryForObject("INSERT INTO journal_entries(tenant_id,entry_number,description,reference_type,reference_id,period_date,posted_by,idempotency_key) VALUES(?,?,?,?,?,?,?,?) RETURNING id",
                Long.class, t(), number, required(request, "description"), nullableText(request.get("referenceType")), nullableText(request.get("referenceId")), date, postedBy, key);
        for (Map<String, Object> line : lines) {
            jdbc.update("INSERT INTO journal_lines(tenant_id,entry_id,account_code,debit_amount,credit_amount,description,currency) VALUES(?,?,?,?,?,?,?)",
                    t(), id, required(line, "accountCode").toUpperCase(Locale.ROOT), money(line.get("debitAmount")), money(line.get("creditAmount")), nullableText(line.get("description")), currency);
        }
        Map<String,Object> saved=journal(Objects.requireNonNull(id));recordAudit("JOURNAL",id,"JOURNAL_POST",actorId,actor,null,saved);return saved;
    }

    public Map<String, Object> journalDrafts(String status) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !Set.of("DRAFT", "PENDING_APPROVAL", "REJECTED", "POSTED").contains(normalized)) {
            badRequest("Unsupported journal draft status.");
        }
        List<Long> ids = jdbc.queryForList("SELECT id FROM journal_drafts WHERE tenant_id=? AND (CAST(? AS VARCHAR) IS NULL OR status=?) ORDER BY created_at DESC,id DESC LIMIT 200",
                Long.class, t(), normalized, normalized);
        return Map.of("items", ids.stream().map(this::journalDraft).toList());
    }

    @Transactional
    public Map<String, Object> createJournalDraft(Map<String, Object> request, Long actorId, String actor) {
        long creatorId = requiredActorId(actorId);
        String creatorEmail = requiredActorEmail(actor);
        String key = required(request, "idempotencyKey");
        List<Map<String, Object>> lines = mapList(request.get("lines"));
        if (lines.size() < 2) badRequest("A journal needs at least two lines.");
        LocalDate date = parseDate(request.get("periodDate"), LocalDate.now());
        String currency = text(request.getOrDefault("currency", "USD")).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter ISO code.");
        BigDecimal debits = BigDecimal.ZERO, credits = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            String accountCode = required(line, "accountCode").toUpperCase(Locale.ROOT);
            if (!exists("SELECT EXISTS (SELECT 1 FROM accounts WHERE code=? AND is_active=TRUE AND tenant_id=?)", accountCode, t())) badRequest("Unknown or inactive account: " + accountCode);
            BigDecimal debit = money(line.get("debitAmount")); BigDecimal credit = money(line.get("creditAmount"));
            if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() == 0) == (credit.signum() == 0)) badRequest("Each journal line must have exactly one positive debit or credit.");
            if (line.containsKey("currency") && !currency.equalsIgnoreCase(text(line.get("currency")))) badRequest("A journal entry must use one currency.");
            debits = debits.add(debit); credits = credits.add(credit);
        }
        if (debits.compareTo(credits) != 0) badRequest("Journal entry is not balanced.");
        ensureAccountingDateOpen(date);
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id,created_by_user_id AS \"createdByUserId\" FROM journal_drafts WHERE idempotency_key=? AND tenant_id=?", key, t());
        if (!existing.isEmpty()) {
            Map<String, Object> found = existing.get(0);
            if (((Number) found.get("createdByUserId")).longValue() != creatorId) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This idempotency key is already used by another user.");
            }
            return journalDraft(((Number) found.get("id")).longValue());
        }
        Long id = jdbc.queryForObject("INSERT INTO journal_drafts(tenant_id,description,reference_type,reference_id,period_date,currency,idempotency_key,created_by_user_id,created_by_email) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class, t(), required(request, "description"), nullableText(request.get("referenceType")), nullableText(request.get("referenceId")), date, currency, key, creatorId, creatorEmail);
        for (Map<String, Object> line : lines) {
            jdbc.update("INSERT INTO journal_draft_lines(tenant_id,draft_id,account_code,debit_amount,credit_amount,description,currency) VALUES(?,?,?,?,?,?,?)",
                    t(), id, required(line, "accountCode").toUpperCase(Locale.ROOT), money(line.get("debitAmount")), money(line.get("creditAmount")), nullableText(line.get("description")), currency);
        }
        Map<String, Object> saved = journalDraft(Objects.requireNonNull(id));
        recordAudit("JOURNAL_DRAFT", id, "JOURNAL_DRAFT_CREATE", creatorId, creatorEmail, null, saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> updateJournalDraft(long id, Map<String, Object> request, Long actorId, String actor) {
        long creatorId = requiredActorId(actorId);
        String email = requiredActorEmail(actor);
        Map<String, Object> before = journalDraftForUpdate(id);
        if (((Number) before.get("createdByUserId")).longValue() != creatorId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the draft creator can edit it.");
        }
        if (!Set.of("DRAFT", "REJECTED").contains(String.valueOf(before.get("status")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft or rejected journal can be edited.");
        }
        String description = required(request, "description");
        LocalDate date = parseDate(request.get("periodDate"), null);
        String currency = text(request.getOrDefault("currency", before.get("currency"))).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter ISO code.");
        List<Map<String, Object>> lines = mapList(request.get("lines"));
        if (lines.size() < 2) badRequest("A journal needs at least two lines.");
        BigDecimal debits = BigDecimal.ZERO, credits = BigDecimal.ZERO;
        for (Map<String, Object> line : lines) {
            String accountCode = required(line, "accountCode").toUpperCase(Locale.ROOT);
            if (!exists("SELECT EXISTS (SELECT 1 FROM accounts WHERE code=? AND is_active=TRUE AND tenant_id=?)", accountCode, t())) badRequest("Unknown or inactive account: " + accountCode);
            BigDecimal debit = money(line.get("debitAmount")); BigDecimal credit = money(line.get("creditAmount"));
            if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() == 0) == (credit.signum() == 0)) badRequest("Each journal line must have exactly one positive debit or credit.");
            if (line.containsKey("currency") && !currency.equalsIgnoreCase(text(line.get("currency")))) badRequest("A journal entry must use one currency.");
            debits = debits.add(debit); credits = credits.add(credit);
        }
        if (debits.compareTo(credits) != 0) badRequest("Journal entry is not balanced.");
        ensureAccountingDateOpen(date);
        jdbc.update("UPDATE journal_drafts SET description=?,reference_type=?,reference_id=?,period_date=?,currency=?,updated_at=NOW() WHERE id=? AND tenant_id=?",
                description, nullableText(request.get("referenceType")), nullableText(request.get("referenceId")), date, currency, id, t());
        jdbc.update("DELETE FROM journal_draft_lines WHERE draft_id=? AND tenant_id=?", id, t());
        for (Map<String, Object> line : lines) {
            jdbc.update("INSERT INTO journal_draft_lines(tenant_id,draft_id,account_code,debit_amount,credit_amount,description,currency) VALUES(?,?,?,?,?,?,?)",
                    t(), id, required(line, "accountCode").toUpperCase(Locale.ROOT), money(line.get("debitAmount")), money(line.get("creditAmount")), nullableText(line.get("description")), currency);
        }
        Map<String, Object> saved = journalDraft(id);
        recordAudit("JOURNAL_DRAFT", id, "JOURNAL_DRAFT_UPDATE", creatorId, email, before, saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> submitJournalDraft(long id, Long actorId, String actor) {
        long userId = requiredActorId(actorId);
        String email = requiredActorEmail(actor);
        Map<String, Object> draft = journalDraftForUpdate(id);
        if (((Number) draft.get("createdByUserId")).longValue() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the draft creator can submit it for approval.");
        }
        if (!Set.of("DRAFT", "REJECTED").contains(String.valueOf(draft.get("status")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft or rejected journal can be submitted.");
        }
        ensureAccountingDateOpen(parseDate(draft.get("periodDate"), null));
        jdbc.update("UPDATE journal_drafts SET status='PENDING_APPROVAL',submitted_at=NOW(),decided_at=NULL,decided_by_user_id=NULL,decided_by_email=NULL,decision_note=NULL,updated_at=NOW() WHERE id=? AND tenant_id=?", id, t());
        Map<String, Object> saved = journalDraft(id);
        recordAudit("JOURNAL_DRAFT", id, "JOURNAL_DRAFT_SUBMIT", userId, email, draft, saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> approveJournalDraft(long id, Long actorId, String actor) {
        long approverId = requiredActorId(actorId);
        String email = requiredActorEmail(actor);
        Map<String, Object> draft = journalDraftForUpdate(id);
        if (!"PENDING_APPROVAL".equals(draft.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending journal can be approved.");
        }
        if (((Number) draft.get("createdByUserId")).longValue() == approverId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A journal draft must be approved by a different user.");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("idempotencyKey", "approved-journal-draft:" + id);
        request.put("description", draft.get("description"));
        request.put("referenceType", draft.get("referenceType"));
        request.put("referenceId", draft.get("referenceId"));
        request.put("periodDate", draft.get("periodDate"));
        request.put("currency", draft.get("currency"));
        request.put("lines", draft.get("lines"));
        Map<String, Object> posted = createJournal(request, approverId, email);
        jdbc.update("UPDATE journal_drafts SET status='POSTED',posted_journal_entry_id=?,decided_at=NOW(),decided_by_user_id=?,decided_by_email=?,decision_note=NULL,updated_at=NOW() WHERE id=? AND tenant_id=?",
                posted.get("id"), approverId, email, id, t());
        Map<String, Object> saved = journalDraft(id);
        recordAudit("JOURNAL_DRAFT", id, "JOURNAL_DRAFT_APPROVE", approverId, email, draft, saved);
        return saved;
    }

    @Transactional
    public Map<String, Object> rejectJournalDraft(long id, String reason, Long actorId, String actor) {
        long approverId = requiredActorId(actorId);
        String email = requiredActorEmail(actor);
        String note = reason == null ? "" : reason.trim();
        if (note.length() < 10) badRequest("A rejection reason of at least 10 characters is required.");
        Map<String, Object> draft = journalDraftForUpdate(id);
        if (!"PENDING_APPROVAL".equals(draft.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending journal can be rejected.");
        }
        if (((Number) draft.get("createdByUserId")).longValue() == approverId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A journal draft must be rejected by a different user.");
        }
        jdbc.update("UPDATE journal_drafts SET status='REJECTED',decided_at=NOW(),decided_by_user_id=?,decided_by_email=?,decision_note=?,updated_at=NOW() WHERE id=? AND tenant_id=?",
                approverId, email, note, id, t());
        Map<String, Object> saved = journalDraft(id);
        recordAudit("JOURNAL_DRAFT", id, "JOURNAL_DRAFT_REJECT", approverId, email, draft, saved);
        return saved;
    }

    private Map<String, Object> journalDraft(long id) {
        Map<String, Object> draft = jdbc.query("SELECT id,description,reference_type AS \"referenceType\",reference_id AS \"referenceId\",period_date AS \"periodDate\",currency,idempotency_key AS \"idempotencyKey\",status,created_by_user_id AS \"createdByUserId\",created_by_email AS \"createdByEmail\",submitted_at AS \"submittedAt\",decided_at AS \"decidedAt\",decided_by_user_id AS \"decidedByUserId\",decided_by_email AS \"decidedByEmail\",decision_note AS \"decisionNote\",posted_journal_entry_id AS \"postedJournalEntryId\",created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM journal_drafts WHERE id=? AND tenant_id=?",
                mapRow(), id, t()).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal draft not found."));
        draft.put("lines", jdbc.queryForList("SELECT id,account_code AS \"accountCode\",debit_amount AS \"debitAmount\",credit_amount AS \"creditAmount\",description,currency FROM journal_draft_lines WHERE draft_id=? AND tenant_id=? ORDER BY id", id, t()));
        return draft;
    }

    private Map<String, Object> journalDraftForUpdate(long id) {
        jdbc.query("SELECT id FROM journal_drafts WHERE id=? AND tenant_id=? FOR UPDATE", (rs, row) -> rs.getLong(1), id, t())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal draft not found."));
        return journalDraft(id);
    }

    private long requiredActorId(Long actorId) {
        if (actorId == null || actorId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A verified user identity is required.");
        return actorId;
    }

    private String requiredActorEmail(String actor) {
        if (actor == null || actor.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A verified user identity is required.");
        return actor.trim();
    }

    @Transactional
    public Map<String, Object> reverseJournal(long id, String reason, Long actorId, String actor) {
        Map<String, Object> original = journal(id);
        if (Boolean.TRUE.equals(original.get("reversal"))) badRequest("A reversal entry cannot itself be reversed.");
        if (exists("SELECT EXISTS (SELECT 1 FROM journal_entries WHERE reversal_of_id=? AND tenant_id=?)", id, t())) badRequest("This journal entry has already been reversed.");
        String key = "reverse:" + id;
        if (exists("SELECT EXISTS (SELECT 1 FROM journal_entries WHERE idempotency_key=? AND tenant_id=?)", key, t())) {
            Long existing = jdbc.queryForObject("SELECT id FROM journal_entries WHERE idempotency_key=? AND tenant_id=?", Long.class, key, t());
            return journal(existing);
        }
        ensureAccountingDateOpen(LocalDate.now());
        List<Map<String, Object>> originalLines = journalLines(id);
        List<Map<String, Object>> reverseLines = originalLines.stream().map(line -> {
            Map<String, Object> reversed = new LinkedHashMap<>();
            reversed.put("accountCode", line.get("accountCode")); reversed.put("debitAmount", line.get("creditAmount"));
            reversed.put("creditAmount", line.get("debitAmount")); reversed.put("currency", line.get("currency"));
            reversed.put("description", "Reversal of " + original.get("entryNumber")); return reversed;
        }).toList();
        LocalDate date = LocalDate.now();
        Long reversalId = jdbc.queryForObject("INSERT INTO journal_entries(tenant_id,entry_number,description,reference_type,reference_id,period_date,posted_by,idempotency_key,is_reversal,reversal_of_id) VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class, t(), "REV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT),
                "Reversal of " + original.get("entryNumber") + (reason.isBlank() ? "" : ": " + reason),
                "JOURNAL_REVERSAL", String.valueOf(id), date, actor == null || actor.isBlank() ? "system" : actor, key, true, id);
        for (Map<String, Object> line : reverseLines) {
            jdbc.update("INSERT INTO journal_lines(tenant_id,entry_id,account_code,debit_amount,credit_amount,description,currency) VALUES(?,?,?,?,?,?,?)",
                    t(), reversalId, line.get("accountCode"), line.get("debitAmount"), line.get("creditAmount"), line.get("description"), line.get("currency"));
        }
        Map<String,Object> saved=journal(Objects.requireNonNull(reversalId));recordAudit("JOURNAL",reversalId,"JOURNAL_REVERSAL",actorId,actor,original,saved);return saved;
    }

    public Map<String, Object> invoices(String status, String type, String term, int page, int size) {
        page = boundedPage(page); size = boundedSize(size);
        String normalizedStatus = status == null ? null : status.toUpperCase(Locale.ROOT);
        String normalizedType = type == null ? null : type.toUpperCase(Locale.ROOT);
        String search = term == null || term.isBlank() ? null : "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        String where = " WHERE tenant_id=? AND (CAST(? AS VARCHAR) IS NULL OR status=?) AND (CAST(? AS VARCHAR) IS NULL OR invoice_type=?) AND (CAST(? AS VARCHAR) IS NULL OR lower(invoice_number) LIKE ? OR lower(coalesce(customer_email,'')) LIKE ? OR cast(order_id as text) LIKE ?)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM invoices" + where, Long.class, t(), normalizedStatus, normalizedStatus, normalizedType, normalizedType, search, search, search, search);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, invoice_number AS \"invoiceNumber\", user_id AS \"userId\", customer_email AS \"customerEmail\", order_id AS \"orderId\", invoice_type AS type, status, subtotal, tax_amount AS \"taxAmount\", total_amount AS \"totalAmount\", currency, issue_date AS \"issueDate\", due_date AS \"dueDate\", paid_date AS \"paidDate\", created_at AS \"createdAt\" FROM invoices" + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                t(), normalizedStatus, normalizedStatus, normalizedType, normalizedType, search, search, search, search, size, page * size);
        return pageResult(rows, page, size, total == null ? 0 : total);
    }

    public Map<String, Object> invoice(long id) {
        Map<String, Object> result = jdbc.query("SELECT id, invoice_number AS \"invoiceNumber\", user_id AS \"userId\", customer_email AS \"customerEmail\", order_id AS \"orderId\", invoice_type AS type, status, subtotal, tax_amount AS \"taxAmount\", total_amount AS \"totalAmount\", currency, issue_date AS \"issueDate\", due_date AS \"dueDate\", paid_date AS \"paidDate\", notes, created_by AS \"createdBy\", created_at AS \"createdAt\", updated_at AS \"updatedAt\" FROM invoices WHERE id=? AND tenant_id=?",
                (rs, row) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    for (String col : List.of("id", "invoiceNumber", "userId", "customerEmail", "orderId", "type", "status", "subtotal", "taxAmount", "totalAmount", "currency", "issueDate", "dueDate", "paidDate", "notes", "createdBy", "createdAt", "updatedAt")) item.put(col, rs.getObject(col));
                    return item;
                }, id, t()).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        result.put("lines", jdbc.queryForList("SELECT id, description, quantity, unit_price AS \"unitPrice\", tax_rate AS \"taxRate\", line_total AS \"lineTotal\", product_id AS \"productId\" FROM invoice_line_items WHERE invoice_id=? ORDER BY id", id));
        return result;
    }

    @Transactional
    public Map<String, Object> createInvoice(Map<String, Object> request, Long actorId, String actor) {
        String key = required(request, "idempotencyKey");
        List<Map<String, Object>> lines = mapList(request.get("lineItems"));
        BigDecimal subtotal = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        List<Map<String, Object>> calculated = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            BigDecimal quantity = decimal(line.get("quantity"), BigDecimal.ONE);
            BigDecimal unitPrice = money(line.get("unitPrice"));
            BigDecimal rate = decimal(line.get("taxRate"), BigDecimal.ZERO);
            if (quantity.signum() <= 0 || unitPrice.signum() < 0 || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) badRequest("Invoice line has invalid quantity, price, or tax rate.");
            BigDecimal lineSubtotal = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineSubtotal.multiply(rate).setScale(4, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineSubtotal); tax = tax.add(lineTax);
            Map<String, Object> item = new LinkedHashMap<>(line); item.put("quantity", quantity); item.put("unitPrice", unitPrice);
            item.put("taxRate", rate); item.put("lineTotal", lineSubtotal.add(lineTax)); calculated.add(item);
        }
        if (calculated.isEmpty()) {
            subtotal = money(request.get("subtotal")); tax = money(request.get("taxAmount"));
            if (subtotal.signum() < 0 || tax.signum() < 0) badRequest("Invoice totals cannot be negative.");
        }
        BigDecimal total = subtotal.add(tax).setScale(4, RoundingMode.HALF_UP);
        String type = text(request.getOrDefault("type", "INVOICE")).toUpperCase(Locale.ROOT);
        if (!Set.of("INVOICE", "CREDIT_NOTE").contains(type)) badRequest("type must be INVOICE or CREDIT_NOTE.");
        String currency = text(request.getOrDefault("currency", "USD")).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter ISO code.");
        Long old = jdbc.query("SELECT id FROM invoices WHERE idempotency_key=? AND tenant_id=?", (rs, row) -> rs.getLong(1), key, t()).stream().findFirst().orElse(null);
        if (old != null) return invoice(old);
        String number = text(request.get("invoiceNumber"));
        if (number.isBlank()) number = (type.equals("CREDIT_NOTE") ? "CN-" : "INV-") + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        LocalDate issueDate = parseDate(request.get("issueDate"), null);
        LocalDate dueDate = parseDate(request.get("dueDate"), null);
        Long id = jdbc.queryForObject("INSERT INTO invoices(tenant_id,invoice_number,user_id,customer_email,order_id,invoice_type,status,subtotal,tax_amount,total_amount,currency,issue_date,due_date,notes,idempotency_key,created_by) VALUES(?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?) RETURNING id",
                Long.class, t(), number, requiredLong(request, "userId"), nullableText(request.get("customerEmail")), nullableLong(request.get("orderId")), type,
                subtotal, tax, total, currency, issueDate, dueDate, nullableText(request.get("notes")), key, actor == null || actor.isBlank() ? "system" : actor);
        for (Map<String, Object> line : calculated) {
            jdbc.update("INSERT INTO invoice_line_items(invoice_id,description,quantity,unit_price,tax_rate,line_total,product_id) VALUES(?,?,?,?,?,?,?)",
                    id, required(line, "description"), line.get("quantity"), line.get("unitPrice"), line.get("taxRate"), line.get("lineTotal"), nullableLong(line.get("productId")));
        }
        Map<String,Object> saved=invoice(Objects.requireNonNull(id));recordAudit("INVOICE",id,"INVOICE_CREATE",actorId,actor,null,saved);return saved;
    }

    @Transactional
    public Map<String, Object> updateInvoice(long id, Map<String, Object> request, Long actorId, String actor) {
        Map<String, Object> before = invoice(id);
        if (!"DRAFT".equals(String.valueOf(before.get("status"))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft invoices can be edited.");

        Long userId = request.containsKey("userId") ? requiredLong(request, "userId") : requiredLong(before, "userId");
        String type = text(request.getOrDefault("type", before.get("type"))).toUpperCase(Locale.ROOT);
        if (!Set.of("INVOICE", "CREDIT_NOTE").contains(type)) badRequest("type must be INVOICE or CREDIT_NOTE.");
        String currency = text(request.getOrDefault("currency", before.get("currency"))).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter ISO code.");
        String email = request.containsKey("customerEmail") ? nullableText(request.get("customerEmail")) : nullableText(before.get("customerEmail"));
        Long orderId = request.containsKey("orderId") ? nullableLong(request.get("orderId")) : nullableLong(before.get("orderId"));
        LocalDate dueDate = parseDate(request.containsKey("dueDate") ? request.get("dueDate") : before.get("dueDate"), null);
        String notes = request.containsKey("notes") ? nullableText(request.get("notes")) : nullableText(before.get("notes"));

        BigDecimal subtotal = toBigDecimal(before.get("subtotal"));
        BigDecimal tax = toBigDecimal(before.get("taxAmount"));
        List<Map<String, Object>> calculated = new ArrayList<>();
        if (request.containsKey("lineItems")) {
            List<Map<String, Object>> lines = mapList(request.get("lineItems"));
            if (lines.isEmpty()) badRequest("A draft invoice must contain at least one line item.");
            subtotal = BigDecimal.ZERO; tax = BigDecimal.ZERO;
            for (Map<String, Object> line : lines) {
                BigDecimal quantity = decimal(line.get("quantity"), BigDecimal.ONE);
                BigDecimal unitPrice = money(line.get("unitPrice"));
                BigDecimal rate = decimal(line.get("taxRate"), BigDecimal.ZERO);
                if (quantity.signum() <= 0 || unitPrice.signum() < 0 || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
                    badRequest("Invoice line has invalid quantity, price, or tax rate.");
                BigDecimal lineSubtotal = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
                BigDecimal lineTax = lineSubtotal.multiply(rate).setScale(4, RoundingMode.HALF_UP);
                subtotal = subtotal.add(lineSubtotal); tax = tax.add(lineTax);
                Map<String, Object> item = new LinkedHashMap<>(line); item.put("quantity", quantity); item.put("unitPrice", unitPrice);
                item.put("taxRate", rate); item.put("lineTotal", lineSubtotal.add(lineTax)); calculated.add(item);
            }
        }

        jdbc.update("UPDATE invoices SET user_id=?,customer_email=?,order_id=?,invoice_type=?,subtotal=?,tax_amount=?,total_amount=?,currency=?,due_date=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND status='DRAFT'",
                userId, email, orderId, type, subtotal, tax, subtotal.add(tax).setScale(4, RoundingMode.HALF_UP), currency, dueDate, notes, id, t());
        if (request.containsKey("lineItems")) {
            jdbc.update("DELETE FROM invoice_line_items WHERE invoice_id=?", id);
            for (Map<String, Object> line : calculated) {
                jdbc.update("INSERT INTO invoice_line_items(invoice_id,description,quantity,unit_price,tax_rate,line_total,product_id) VALUES(?,?,?,?,?,?,?)",
                        id, required(line, "description"), line.get("quantity"), line.get("unitPrice"), line.get("taxRate"), line.get("lineTotal"), nullableLong(line.get("productId")));
            }
        }
        Map<String,Object> updated = invoice(id);
        recordAudit("INVOICE", id, "INVOICE_UPDATE", actorId, actor, before, updated);
        return updated;
    }

    @Transactional
    public Map<String, Object> updateInvoiceStatus(long id, String next, Long actorId, String actor) {
        Map<String, Object> old = invoice(id);
        String current = String.valueOf(old.get("status"));
        next = next.toUpperCase(Locale.ROOT);
        if (!INVOICE_STATES.contains(next)) badRequest("Unsupported invoice status.");
        boolean valid = (current.equals("DRAFT") && Set.of("ISSUED", "VOID").contains(next))
                || (current.equals("ISSUED") && Set.of("PAID", "OVERDUE", "VOID").contains(next))
                || (current.equals("OVERDUE") && Set.of("PAID", "VOID").contains(next));
        if (current.equals(next)) return old;
        if (!valid) badRequest("Invalid invoice status transition from " + current + " to " + next + ".");
        jdbc.update("UPDATE invoices SET status=?, issue_date=CASE WHEN ?='ISSUED' AND issue_date IS NULL THEN CURRENT_DATE ELSE issue_date END, paid_date=CASE WHEN ?='PAID' THEN CURRENT_DATE ELSE paid_date END, updated_at=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                next, next, next, id, t());
        Map<String,Object> updated=invoice(id);recordAudit("INVOICE",id,"INVOICE_STATUS_"+next,actorId,actor,old,updated);return updated;
    }

    public Map<String, Object> profitLoss(LocalDate start, LocalDate end, String currency) {
        validatePeriod(start, end); String c = validCurrency(currency);
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT a.code AS \"accountCode\",a.name,a.account_type AS \"accountType\",COALESCE(SUM(l.debit_amount),0) AS debits,COALESCE(SUM(l.credit_amount),0) AS credits FROM accounts a LEFT JOIN journal_entries j ON j.tenant_id=a.tenant_id AND j.period_date BETWEEN ? AND ? LEFT JOIN journal_lines l ON l.entry_id=j.id AND l.account_code=a.code AND l.currency=? WHERE a.tenant_id=? AND a.account_type IN ('REVENUE','EXPENSE') GROUP BY a.code,a.name,a.account_type ORDER BY a.code", start, end, c, t());
        BigDecimal revenue = BigDecimal.ZERO, expenses = BigDecimal.ZERO;
        for (Map<String, Object> row : lines) {
            BigDecimal debit = toBigDecimal(row.get("debits")), credit = toBigDecimal(row.get("credits"));
            BigDecimal amount = "REVENUE".equals(row.get("accountType")) ? credit.subtract(debit) : debit.subtract(credit);
            row.put("amount", amount); if ("REVENUE".equals(row.get("accountType"))) revenue = revenue.add(amount); else expenses = expenses.add(amount);
        }
        Map<String, Object> report = reportBase("PROFIT_LOSS", start, end, c);
        report.put("revenue", revenue); report.put("expenses", expenses); report.put("netIncome", revenue.subtract(expenses)); report.put("accounts", lines); return report;
    }

    public Map<String, Object> balanceSheet(LocalDate asOf, String currency) {
        String c = validCurrency(currency);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.code AS \"accountCode\",a.name,a.account_type AS \"accountType\",a.normal_balance AS \"normalBalance\",COALESCE(SUM(l.debit_amount),0) AS debits,COALESCE(SUM(l.credit_amount),0) AS credits FROM accounts a LEFT JOIN journal_entries j ON j.tenant_id=a.tenant_id AND j.period_date<=? LEFT JOIN journal_lines l ON l.entry_id=j.id AND l.account_code=a.code AND l.currency=? WHERE a.tenant_id=? AND a.account_type IN ('ASSET','LIABILITY','EQUITY') GROUP BY a.code,a.name,a.account_type,a.normal_balance ORDER BY a.code", asOf, c, t());
        BigDecimal assets = BigDecimal.ZERO, liabilities = BigDecimal.ZERO, equity = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal debit = toBigDecimal(row.get("debits")), credit = toBigDecimal(row.get("credits"));
            BigDecimal balance = "DEBIT".equals(row.get("normalBalance")) ? debit.subtract(credit) : credit.subtract(debit);
            row.put("balance", balance);
            switch (String.valueOf(row.get("accountType"))) { case "ASSET" -> assets = assets.add(balance); case "LIABILITY" -> liabilities = liabilities.add(balance); case "EQUITY" -> equity = equity.add(balance); }
        }
        Map<String, Object> report = reportBase("BALANCE_SHEET", null, asOf, c);
        report.put("assets", assets); report.put("liabilities", liabilities); report.put("equity", equity); report.put("liabilitiesAndEquity", liabilities.add(equity)); report.put("accounts", rows); return report;
    }

    public Map<String, Object> taxSummary(LocalDate start, LocalDate end, String currency) {
        validatePeriod(start, end); String c = validCurrency(currency);
        Map<String, Object> totals = jdbc.queryForMap("SELECT COALESCE(SUM(subtotal),0) AS subtotal,COALESCE(SUM(tax_amount),0) AS tax,COALESCE(SUM(total_amount),0) AS total,COUNT(*) AS invoice_count FROM invoices WHERE tenant_id=? AND currency=? AND issue_date BETWEEN ? AND ? AND status IN ('ISSUED','PAID','OVERDUE')", t(), c, start, end);
        Map<String, Object> report = reportBase("TAX_SUMMARY", start, end, c); report.putAll(totals); return report;
    }

    public Map<String, Object> cashFlow(LocalDate start, LocalDate end, String currency) {
        validatePeriod(start, end); String c = validCurrency(currency);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.code AS \"accountCode\",a.name,COALESCE(SUM(l.debit_amount),0) AS inflows,COALESCE(SUM(l.credit_amount),0) AS outflows FROM accounts a LEFT JOIN journal_entries j ON j.tenant_id=a.tenant_id AND j.period_date BETWEEN ? AND ? LEFT JOIN journal_lines l ON l.entry_id=j.id AND l.account_code=a.code AND l.currency=? WHERE a.tenant_id=? AND a.is_cash_account=TRUE GROUP BY a.code,a.name ORDER BY a.code", start, end, c, t());
        BigDecimal inflows = BigDecimal.ZERO, outflows = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) { inflows = inflows.add(toBigDecimal(row.get("inflows"))); outflows = outflows.add(toBigDecimal(row.get("outflows"))); }
        Map<String, Object> report = reportBase("CASH_FLOW", start, end, c); report.put("inflows", inflows); report.put("outflows", outflows); report.put("netCashFlow", inflows.subtract(outflows)); report.put("cashAccounts", rows); return report;
    }

    public List<Map<String, Object>> revenueTrends(LocalDate start, LocalDate end, String currency) {
        validatePeriod(start, end); String c = validCurrency(currency);
        return jdbc.queryForList("SELECT TO_CHAR(DATE_TRUNC('month',created_at),'YYYY-MM') AS month,COALESCE(SUM(CASE WHEN transaction_type='ORDER_PAYMENT' AND status='POSTED' THEN amount ELSE 0 END),0) AS revenue,COALESCE(SUM(CASE WHEN transaction_type='ORDER_REFUND' AND status='POSTED' THEN amount ELSE 0 END),0) AS refunds FROM ledger_transactions WHERE tenant_id=? AND currency=? AND created_at>=? AND created_at<? GROUP BY DATE_TRUNC('month',created_at) ORDER BY DATE_TRUNC('month',created_at)", t(), c, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    public byte[] exportJournalCsv(Long actorId, String actor) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT j.entry_number,j.period_date,j.description,j.reference_type,j.reference_id,l.account_code,l.debit_amount,l.credit_amount,l.currency,l.description AS line_description FROM journal_entries j JOIN journal_lines l ON l.entry_id=j.id AND l.tenant_id=j.tenant_id WHERE j.tenant_id=? ORDER BY j.period_date,j.id,l.id", t());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("entry_number,period_date,description,reference_type,reference_id,account_code,debit_amount,credit_amount,currency,line_description");
            for (Map<String, Object> row : rows) writer.println(row.values().stream().map(v -> csv(v == null ? "" : String.valueOf(v))).reduce((a,b) -> a + "," + b).orElse(""));
        }
        recordAudit("JOURNAL",null,"JOURNAL_CSV_EXPORT",actorId,actor,null,Map.of("rows",rows.size()));
        return out.toByteArray();
    }

    @Transactional
    public Map<String, Object> importCsv(MultipartFile file, String type, Long actorId, String actor) {
        if (file == null || file.isEmpty()) badRequest("Select a non-empty CSV file.");
        if (file.getSize() > 5 * 1024 * 1024) badRequest("CSV upload must be 5 MB or smaller.");
        String importType = type.toUpperCase(Locale.ROOT);
        if (!Set.of("BANK_STATEMENT", "INVOICES", "JOURNAL_ENTRIES").contains(importType)) badRequest("Unsupported accounting import type.");
        List<String> errors = new ArrayList<>(); int processed = 0, total = 0;
        try {
            List<String> lines = Arrays.asList(new String(file.getBytes(), StandardCharsets.UTF_8).split("\\R", -1));
            if (lines.size() < 2) badRequest("CSV must include a header and at least one data row.");
            List<String> headers = parseCsv(lines.get(0)); Map<String, Integer> column = new HashMap<>();
            for (int i=0;i<headers.size();i++) column.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
            total = lines.size() - 1;
            Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
            for (int i=1;i<lines.size();i++) {
                try {
                    List<String> values = parseCsv(lines.get(i)); String key = value(values, column, importType.equals("INVOICES") ? "invoice_number" : (importType.equals("BANK_STATEMENT") ? "reference" : "entry_key"));
                    if (key.isBlank()) key = "row-" + (i+1);
                    Map<String, Object> row = new LinkedHashMap<>(); for (String header : column.keySet()) row.put(header, value(values,column,header));
                    row.put("_row", i+1); groups.computeIfAbsent(key, unused -> new ArrayList<>()).add(row);
                } catch (RuntimeException e) { errors.add("Row " + (i+1) + ": " + e.getMessage()); }
            }
            for (Map.Entry<String, List<Map<String, Object>>> group : groups.entrySet()) {
                try {
                    if (importType.equals("INVOICES")) importInvoiceRow(group.getValue().get(0), actorId, actor, group.getKey());
                    else importJournalRows(group.getKey(), group.getValue(), actorId, actor, importType);
                    processed += group.getValue().size();
                } catch (RuntimeException e) { errors.add("Rows " + group.getValue().get(0).get("_row") + "-" + group.getValue().get(group.getValue().size()-1).get("_row") + ": " + safeMessage(e)); }
            }
        } catch (Exception e) { errors.add(safeMessage(e)); }
        String status = errors.isEmpty() ? "COMPLETED" : (processed == 0 ? "FAILED" : "COMPLETED_WITH_ERRORS");
        String errorJson = errors.isEmpty() ? null : "[" + errors.stream().map(s -> "\"" + jsonEscape(s) + "\"").reduce((a,b) -> a + "," + b).orElse("") + "]";
        Long id = jdbc.queryForObject("INSERT INTO accounting_imports(tenant_id,import_type,filename,status,total_rows,processed_rows,error_rows,errors,imported_by) VALUES(?,?,?,?,?,?,?,?::jsonb,?) RETURNING id",
                Long.class, t(), importType, safeFilename(file.getOriginalFilename()), status, total, processed, errors.size(), errorJson, actor == null || actor.isBlank() ? "system" : actor);
        Map<String,Object> result=Map.of("id", id, "type", importType, "status", status, "totalRows", total, "processedRows", processed, "errorRows", errors.size(), "errors", errors);
        recordAudit("IMPORT",id,"ACCOUNTING_IMPORT",actorId,actor,null,result);return result;
    }

    private void importInvoiceRow(Map<String, Object> row, Long actorId, String actor, String key) {
        Map<String, Object> request = new HashMap<>(); request.put("invoiceNumber", key); request.put("userId", requiredLong(row,"user_id"));
        request.put("customerEmail", row.get("customer_email")); request.put("orderId", nullableLong(row.get("order_id")));
        request.put("type", row.getOrDefault("type","INVOICE")); request.put("subtotal", required(row,"subtotal")); request.put("taxAmount", row.getOrDefault("tax_amount","0"));
        request.put("currency", row.getOrDefault("currency","USD")); request.put("issueDate", row.get("issue_date")); request.put("dueDate", row.get("due_date"));
        request.put("notes", row.get("notes")); request.put("idempotencyKey", "csv-invoice:" + key);
        createInvoice(request, actorId, actor);
    }

    private void importJournalRows(String key, List<Map<String,Object>> rows, Long actorId, String actor, String type) {
        List<Map<String,Object>> lines = new ArrayList<>();
        for (Map<String,Object> row : rows) {
            Map<String,Object> line = new HashMap<>(); line.put("accountCode", required(row,"account_code"));
            line.put("debitAmount", row.getOrDefault("debit_amount", "0")); line.put("creditAmount", row.getOrDefault("credit_amount", "0"));
            line.put("description", row.get("line_description")); lines.add(line);
        }
        Map<String,Object> request = new HashMap<>(); request.put("idempotencyKey", "csv:" + type + ":" + key);
        request.put("description", rows.get(0).getOrDefault("description", "Imported " + type));
        request.put("periodDate", rows.get(0).getOrDefault("period_date", LocalDate.now().toString())); request.put("currency", rows.get(0).getOrDefault("currency", "USD"));
        request.put("referenceType", type); request.put("referenceId", key); request.put("lines", lines); createJournal(request, actorId, actor);
    }

    private Map<String,Object> journal(long id) {
        Map<String,Object> entry = jdbc.query("SELECT id,entry_number AS \"entryNumber\",description,reference_type AS \"referenceType\",reference_id AS \"referenceId\",period_date AS \"periodDate\",posted_by AS \"postedBy\",idempotency_key AS \"idempotencyKey\",is_reversal AS reversal,reversal_of_id AS \"reversalOfId\",created_at AS \"createdAt\" FROM journal_entries WHERE id=? AND tenant_id=?", mapRow(), id, t())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Journal entry not found."));
        entry.put("lines", journalLines(id)); return entry;
    }
    private List<Map<String,Object>> journalLines(long id) {
        return jdbc.queryForList("SELECT l.id,l.account_code AS \"accountCode\",a.name AS \"accountName\",l.debit_amount AS \"debitAmount\",l.credit_amount AS \"creditAmount\",l.description,l.currency FROM journal_lines l JOIN accounts a ON a.code=l.account_code AND a.tenant_id=l.tenant_id WHERE l.entry_id=? AND l.tenant_id=? ORDER BY l.id", id, t());
    }
    private void recordAudit(String entityType, Long entityId, String action, Long actorId, String actor,
                             Object before, Object after) {
        jdbc.update("INSERT INTO accounting_audit_log(tenant_id,transaction_id,admin_id,admin_email,action,reason,before_value,after_value,entity_type,entity_id) VALUES(?,NULL,?,?,?,?,?,?,?,?)",
                t(), actorId, actor == null || actor.isBlank() ? "unknown" : actor, action,
                action.replace('_',' '), before == null ? null : String.valueOf(before), String.valueOf(after), entityType, entityId);
    }
    private Map<String,Object> reportBase(String type, LocalDate start, LocalDate end, String currency) {
        Map<String,Object> report = new LinkedHashMap<>(); report.put("reportType",type); report.put("periodStart",start); report.put("periodEnd",end); report.put("currency",currency); report.put("generatedAt",LocalDateTime.now());
        report.put("disclaimer","Reports reflect recorded entries only. Reconcile imported and external payment records before using them as a final statement."); return report;
    }
    private void validatePeriod(LocalDate start, LocalDate end) { if (start == null || end == null || end.isBefore(start)) badRequest("Provide a valid date range."); }
    private String validCurrency(String currency) { String c = currency == null ? "USD" : currency.toUpperCase(Locale.ROOT); if (!c.matches("[A-Z]{3}")) badRequest("Currency must be a three-letter ISO code."); return c; }
    /** Tenant of the current request (legacy tenant for platform tokens and background work). */
    private static UUID t() { return AccountingTenantContext.current(); }
    private boolean exists(String sql,Object... args) { return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args)); }
    private Map<String,Object> pageResult(List<Map<String,Object>> rows,int page,int size,long total) { return Map.of("items",rows,"page",page,"size",size,"total",total,"totalPages",(int)Math.ceil((double)total/size)); }
    private int boundedPage(int page) { if(page<0) badRequest("page must not be negative."); return page; }
    private int boundedSize(int size) { if(size<1||size>100) badRequest("size must be between 1 and 100."); return size; }
    private RowMapper<Map<String,Object>> mapRow() { return (rs,n) -> { Map<String,Object> m=new LinkedHashMap<>(); var md=rs.getMetaData(); for(int i=1;i<=md.getColumnCount();i++) m.put(md.getColumnLabel(i),rs.getObject(i)); return m; }; }
    private static List<Map<String,Object>> mapList(Object value) { if (!(value instanceof List<?> list)) return List.of(); List<Map<String,Object>> out=new ArrayList<>(); for(Object item:list) { if (!(item instanceof Map<?,?>)) badRequest("lines must contain objects."); Map<?,?> map=(Map<?,?>) item; Map<String,Object> row=new HashMap<>(); map.forEach((k,v)->row.put(String.valueOf(k),v)); out.add(row); } return out; }
    private static String required(Map<String,Object> m,String k) { String value=text(m.get(k)).trim(); if(value.isBlank()) badRequest(k+" is required."); return value; }
    private static String text(Object v) { return v == null ? "" : String.valueOf(v); }
    private static String nullableText(Object v) { String s=text(v).trim(); return s.isEmpty()?null:s; }
    private static BigDecimal money(Object v) { return decimal(v, BigDecimal.ZERO).setScale(4,RoundingMode.HALF_UP); }
    private static BigDecimal decimal(Object v,BigDecimal fallback) { if(v==null||String.valueOf(v).isBlank())return fallback; try{return new BigDecimal(String.valueOf(v));}catch(NumberFormatException e){badRequest("Invalid numeric value."); return fallback;} }
    private static BigDecimal toBigDecimal(Object v) { return v instanceof BigDecimal d ? d : new BigDecimal(String.valueOf(v)); }
    private static LocalDate parseDate(Object v,LocalDate fallback) { if(v==null||String.valueOf(v).isBlank())return fallback; try{return LocalDate.parse(String.valueOf(v));}catch(Exception e){badRequest("Dates must use YYYY-MM-DD.");return fallback;} }
    private static Long requiredLong(Map<String,Object> m,String k) { Long n=nullableLong(m.get(k)); if(n==null||n<=0)badRequest(k+" must be a positive integer."); return n; }
    private static Long nullableLong(Object v) { if(v==null||String.valueOf(v).isBlank())return null; try{return Long.valueOf(String.valueOf(v));}catch(Exception e){badRequest("Invalid integer value.");return null;} }
    private static String csv(String value) { return "\""+value.replace("\"","\"\"")+"\""; }
    private static List<String> parseCsv(String line) { List<String> out=new ArrayList<>(); StringBuilder value=new StringBuilder(); boolean quoted=false; for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='\"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='\"'){value.append('\"');i++;}else quoted=!quoted;}else if(c==','&&!quoted){out.add(value.toString());value.setLength(0);}else value.append(c);} if(quoted)badRequest("CSV contains an unterminated quote.");out.add(value.toString());return out; }
    private static String value(List<String> values,Map<String,Integer> columns,String name){Integer at=columns.get(name.toLowerCase(Locale.ROOT));return at==null||at>=values.size()?"":values.get(at).trim();}
    private static String safeFilename(String filename) { if(filename==null||filename.isBlank())return "upload.csv"; return filename.replace('\\','/').substring(filename.replace('\\','/').lastIndexOf('/')+1).replaceAll("[^A-Za-z0-9._-]","_"); }
    private static String safeMessage(Exception e) { String m=e.getMessage(); return m==null?"Invalid row.":m.replaceAll("[\\r\\n]"," ").substring(0,Math.min(m.length(),200)); }
    private static String jsonEscape(String s) { return s.replace("\\","\\\\").replace("\"","\\\"").replace("\r"," ").replace("\n"," "); }
    private static void badRequest(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}

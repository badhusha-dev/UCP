package com.ecommerce.order.rma;

import com.ecommerce.order.admin.AdminSupport;
import com.ecommerce.order.admin.IdempotencyService;
import com.ecommerce.order.admin.security.AdminPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Supplier;

import static com.ecommerce.order.admin.AdminSupport.*;

/**
 * Returns & Refund Management API. Authenticated by AdminAuthenticationFilter (verified JWT, live tenant grant); each endpoint also
 * enforces its own permission, and every query is scoped to the verified tenant and the caller's stores.
 */
@RestController
@RequestMapping("/api/orders/admin/rma")
public class ReturnAdminController {
    private final ReturnService returns;
    private final InspectionService inspections;
    private final RefundService refunds;
    private final ExchangeService exchanges;
    private final PickupService pickups;
    private final ReturnExceptionService exceptions;
    private final ReturnReportService reports;
    private final PolicyService policies;
    private final ReturnSettingsService settings;
    private final AttachmentService attachments;
    private final IdempotencyService idempotency;

    public ReturnAdminController(ReturnService returns, InspectionService inspections, RefundService refunds, ExchangeService exchanges, PickupService pickups, ReturnExceptionService exceptions,
                                 ReturnReportService reports, PolicyService policies, ReturnSettingsService settings, AttachmentService attachments, IdempotencyService idempotency) {
        this.returns = returns; this.inspections = inspections; this.refunds = refunds; this.exchanges = exchanges; this.pickups = pickups; this.exceptions = exceptions;
        this.reports = reports; this.policies = policies; this.settings = settings; this.attachments = attachments; this.idempotency = idempotency;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> locked(OptimisticLockingFailureException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "The record was changed by someone else; reload and try again.")); }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }

    /** With an Idempotency-Key the action runs at most once per key; without one the state guards in the services make repeats harmless (409). */
    private Map<String, Object> once(AdminPrincipal a, String key, String action, long ref, String fingerprint, Supplier<Map<String, Object>> work) {
        return key == null || key.isBlank() ? work.get() : idempotency.run(a, key, action, ref, fingerprint, work);
    }

    private static String text(Map<String, Object> b, String k) { Object v = b == null ? null : b.get(k); return v == null ? null : String.valueOf(v); }

    // ---- meta, dashboard, analytics -----------------------------------------------------------------------------------

    @GetMapping("/meta")
    public Map<String, Object> meta(@AuthenticationPrincipal AdminPrincipal a) {
        require(a, "return:view");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("views", ReturnStateMachine.VIEWS);
        m.put("retailStatuses", ReturnStateMachine.known(ReturnStateMachine.RETAIL)); m.put("claimStatuses", ReturnStateMachine.known(ReturnStateMachine.CLAIM));
        m.put("retailFlow", ReturnStateMachine.flow(ReturnStateMachine.RETAIL)); m.put("claimFlow", ReturnStateMachine.flow(ReturnStateMachine.CLAIM));
        m.put("manualTargets", List.of("UNDER_REVIEW", "CUSTOMER_CANCELLED", "RECEIVED", "INSPECTION_PENDING", "REFUND_PENDING", "CLOSED"));
        m.put("exceptionCategories", ReturnExceptionService.CATEGORIES);
        m.put("permissions", a.permissions().contains("*") ? List.of("*") : a.permissions().stream().filter(p -> p.startsWith("return:")).sorted().toList());
        m.put("canViewCustomerDetails", a.hasPermission("return:view-customer"));
        return m;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AdminPrincipal a, @RequestParam Map<String, String> q) { require(a, "return:view"); return reports.dashboard(a, q); }

    @GetMapping("/analytics")
    public Map<String, Object> analytics(@AuthenticationPrincipal AdminPrincipal a, @RequestParam Map<String, String> q) { require(a, "return:analytics-view"); return reports.analytics(a, q); }

    @GetMapping("/audit")
    public Map<String, Object> audit(@AuthenticationPrincipal AdminPrincipal a, @RequestParam Map<String, String> q) { require(a, "return:audit-view"); return reports.audit(a, q); }

    // ---- returns -------------------------------------------------------------------------------------------------------

    @GetMapping("/returns")
    public Map<String, Object> list(@AuthenticationPrincipal AdminPrincipal a, @RequestParam Map<String, String> q) { require(a, "return:view"); return returns.list(a, q); }

    @GetMapping(value = "/returns/export", produces = "text/csv")
    public ResponseEntity<String> export(@AuthenticationPrincipal AdminPrincipal a, @RequestParam Map<String, String> q) {
        require(a, "return:export");
        Map<String, String> p = new HashMap<>(q); p.put("page", "0"); p.put("size", "100");
        StringBuilder csv = new StringBuilder("rma,order,status,kind,resolution,reason,amount,store,created\n");
        for (int page = 0; page < 50; page++) {
            p.put("page", String.valueOf(page));
            @SuppressWarnings("unchecked") List<Map<String, Object>> rows = (List<Map<String, Object>>) returns.list(a, p).get("items");
            for (Map<String, Object> r : rows) csv.append(csvCell(r.get("rmaNumber"))).append(',').append(csvCell(r.get("orderNumber"))).append(',').append(csvCell(r.get("status"))).append(',').append(csvCell(r.get("returnKind"))).append(',')
                    .append(csvCell(r.get("resolution"))).append(',').append(csvCell(r.get("reasonCode"))).append(',').append(csvCell(r.get("refundAmount"))).append(',').append(csvCell(r.get("storeId"))).append(',').append(csvCell(r.get("createdAt"))).append('\n');
            if (rows.size() < 100) break;
        }
        returns.audit(a.tenantId(), null, "EXPORT", "returns", "EXPORT", ReturnService.Actor.of(a), q.toString());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=returns.csv").header("X-Content-Type-Options", "nosniff").body(csv.toString());
    }

    public record LineBody(Long orderItemId, Integer quantity) {}
    public record CreateBody(Long orderId, String reasonCode, String details, String condition, String resolution, List<LineBody> lines) {}

    @PostMapping("/returns")
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal AdminPrincipal a, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody CreateBody b) {
        require(a, "return:create");
        if (b == null || b.orderId() == null) throw bad("orderId is required.");
        List<ReturnService.Line> lines = (b.lines() == null ? List.<LineBody>of() : b.lines()).stream().map(l -> {
            if (l.orderItemId() == null || l.quantity() == null) throw bad("Each line needs orderItemId and quantity.");
            return new ReturnService.Line(l.orderItemId(), l.quantity());
        }).toList();
        Map<String, Object> out = returns.create(a.tenantId(), a, null, new ReturnService.CreateCommand(b.orderId(), b.reasonCode(), b.details(), lines, b.condition(), b.resolution(), key));
        return ResponseEntity.status(Boolean.TRUE.equals(out.get("idempotentReplay")) ? HttpStatus.OK : HttpStatus.CREATED).body(out);
    }

    @GetMapping("/returns/{id}")
    public Map<String, Object> details(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "return:view"); return returns.details(a, id); }

    @PostMapping("/returns/{id}/approve")
    public Map<String, Object> approve(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        require(a, "return:approve");
        Map<Long, Integer> quantities = null;
        if (b != null && b.get("quantities") instanceof Map<?, ?> m) {
            quantities = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                try { quantities.put(Long.parseLong(String.valueOf(e.getKey())), Integer.parseInt(String.valueOf(e.getValue()))); }
                catch (NumberFormatException ex) { throw bad("quantities must map order item ids to whole numbers."); }
            }
        }
        return returns.approve(a, id, quantities, text(b, "note"));
    }

    @PostMapping("/returns/{id}/reject")
    public Map<String, Object> reject(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody Map<String, Object> b) { require(a, "return:reject"); return returns.reject(a, id, text(b, "reason")); }

    @PostMapping("/returns/{id}/request-info")
    public Map<String, Object> requestInfo(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody Map<String, Object> b) { require(a, "return:review"); return returns.requestInfo(a, id, text(b, "note")); }

    @PostMapping("/returns/{id}/notes")
    public Map<String, Object> note(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody Map<String, Object> b) { require(a, "return:review"); return returns.addNote(a, id, text(b, "body")); }

    @PostMapping("/returns/{id}/assign")
    public Map<String, Object> assign(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody Map<String, Object> b) {
        require(a, "return:review");
        Long op = null;
        if (b.get("operatorId") != null) { try { op = Long.parseLong(String.valueOf(b.get("operatorId"))); } catch (NumberFormatException e) { throw bad("operatorId must be a number."); } if (op <= 0) throw bad("operatorId must be positive."); }
        return returns.assign(a, id, op);
    }

    @PostMapping("/returns/{id}/transition")
    public Map<String, Object> transition(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody Map<String, Object> b) {
        String to = text(b, "to");
        require(a, switch (to == null ? "" : to) {
            case "RECEIVED", "INSPECTION_PENDING" -> "return:receive";
            case "REFUND_PENDING" -> "return:refund-process";
            default -> "return:review";
        });
        return returns.transition(a, id, to, text(b, "reason"));
    }

    // ---- attachments ----------------------------------------------------------------------------------------------------

    @PostMapping(value = "/returns/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "kind", defaultValue = "CUSTOMER_EVIDENCE") String kind, @RequestParam(value = "note", required = false) String note) throws IOException {
        require(a, kind.equals("INSPECTION") ? "return:inspect" : "return:review");
        returns.load(a, id);
        Map<String, Object> out = attachments.upload(a.tenantId(), id, kind, file.getOriginalFilename(), file.getBytes(), note, a.userId(), null);
        returns.audit(a.tenantId(), id, "ATTACHMENT", String.valueOf(out.get("id")), "UPLOAD", ReturnService.Actor.of(a), kind);
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @GetMapping("/returns/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @PathVariable long attachmentId) {
        require(a, "return:view");
        returns.load(a, id);
        AttachmentService.Download d = attachments.download(a.tenantId(), id, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType())).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.fileName() + "\"")
                .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(d.content());
    }

    // ---- inspection -----------------------------------------------------------------------------------------------------

    @PostMapping("/returns/{id}/inspection")
    public Map<String, Object> inspect(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody InspectionService.Command b) { require(a, "return:inspect"); return inspections.record(a, id, b); }

    @GetMapping("/returns/{id}/inspections")
    public List<Map<String, Object>> inspectionsOf(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "return:view"); return inspections.list(a, id); }

    // ---- refunds --------------------------------------------------------------------------------------------------------

    @PostMapping("/returns/{id}/refund")
    public Map<String, Object> prepareRefund(@AuthenticationPrincipal AdminPrincipal a, @RequestHeader(value = "Idempotency-Key", required = false) String key, @PathVariable long id, @RequestBody(required = false) Map<String, Object> b) {
        require(a, "return:refund-process");
        return once(a, key, "RMA_REFUND_PREPARE", id, String.valueOf(text(b, "method")), () -> refunds.prepare(a, id, text(b, "method")));
    }

    @GetMapping("/refunds")
    public Map<String, Object> refundList(@AuthenticationPrincipal AdminPrincipal a, @RequestParam Map<String, String> q) { require(a, "return:view"); return refunds.list(a, q); }

    @GetMapping("/refunds/{id}")
    public Map<String, Object> refund(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "return:view"); return refunds.viewFor(a, id); }

    @PostMapping("/refunds/{id}/approve")
    public Map<String, Object> approveRefund(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "return:refund-process"); return refunds.approve(a, id); }

    @PostMapping("/refunds/{id}/process")
    public Map<String, Object> processRefund(@AuthenticationPrincipal AdminPrincipal a, @RequestHeader(value = "Idempotency-Key", required = false) String key, @PathVariable long id) {
        require(a, "return:refund-process");
        return once(a, key, "RMA_REFUND_PROCESS", id, "", () -> refunds.process(a, id));
    }

    // ---- exchanges ------------------------------------------------------------------------------------------------------

    public record ExchangeBody(Long returnItemId, Long newProductId, String variant, Integer quantity, String note) {}

    @PostMapping("/returns/{id}/exchanges")
    public ResponseEntity<Map<String, Object>> propose(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody ExchangeBody b) {
        require(a, "return:exchange");
        if (b.returnItemId() == null || b.newProductId() == null || b.quantity() == null) throw bad("returnItemId, newProductId and quantity are required.");
        return ResponseEntity.status(HttpStatus.CREATED).body(exchanges.propose(a, id, new ExchangeService.Proposal(b.returnItemId(), b.newProductId(), b.variant(), b.quantity(), b.note())));
    }

    @GetMapping("/exchanges")
    public Map<String, Object> exchangeList(@AuthenticationPrincipal AdminPrincipal a, @RequestParam Map<String, String> q) { require(a, "return:view"); return exchanges.list(a, q); }

    @PostMapping("/exchanges/{id}/decision")
    public Map<String, Object> decideExchange(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody Map<String, Object> b) {
        require(a, "return:exchange");
        return exchanges.decide(a, id, Boolean.TRUE.equals(b.get("approve")), text(b, "note"));
    }

    @PostMapping("/exchanges/{id}/fulfill")
    public Map<String, Object> fulfill(@AuthenticationPrincipal AdminPrincipal a, @RequestHeader(value = "Idempotency-Key", required = false) String key, @PathVariable long id) {
        require(a, "return:exchange");
        return once(a, key, "RMA_EXCHANGE_FULFILL", id, "", () -> exchanges.fulfill(a, id));
    }

    // ---- reverse pickup -------------------------------------------------------------------------------------------------

    public record PickupBody(String address, String phone, OffsetDateTime from, OffsetDateTime to, String destination) {}

    @PostMapping("/returns/{id}/pickup")
    public Map<String, Object> schedulePickup(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody PickupBody b) {
        require(a, "return:pickup");
        return pickups.schedule(a, id, new PickupService.Request(b.address(), b.phone(), b.from(), b.to(), b.destination()));
    }

    @PostMapping("/returns/{id}/pickup/cancel")
    public Map<String, Object> cancelPickup(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody Map<String, Object> b) { require(a, "return:pickup"); return pickups.cancel(a, id, text(b, "reason")); }

    // ---- exceptions -----------------------------------------------------------------------------------------------------

    @GetMapping("/exceptions")
    public Map<String, Object> exceptionList(@AuthenticationPrincipal AdminPrincipal a, @RequestParam Map<String, String> q) { require(a, "return:view"); return exceptions.list(a, q); }

    @PostMapping("/exceptions/{id}/action")
    public Map<String, Object> exceptionAction(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody Map<String, Object> b) {
        require(a, "return:exception-manage");
        Long assignee = null;
        if (b.get("assigneeId") != null) { try { assignee = Long.parseLong(String.valueOf(b.get("assigneeId"))); } catch (NumberFormatException e) { throw bad("assigneeId must be a number."); } }
        return exceptions.act(a, id, text(b, "action"), assignee, text(b, "note"));
    }

    @PostMapping("/returns/{id}/scan")
    public Map<String, Object> scan(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) {
        require(a, "return:exception-manage");
        returns.load(a, id);
        return Map.of("raised", exceptions.scan(a.tenantId(), id));
    }

    // ---- policies -------------------------------------------------------------------------------------------------------

    @GetMapping("/policies")
    public List<Map<String, Object>> policyList(@AuthenticationPrincipal AdminPrincipal a) { require(a, "return:view"); return policies.list(a); }

    @GetMapping("/policies/defaults")
    public Map<String, Object> policyDefaults(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(defaultValue = "RETAIL_RETURN") String kind) { require(a, "return:view"); return policies.defaults(kind); }

    @PostMapping("/policies")
    public ResponseEntity<Map<String, Object>> policyCreate(@AuthenticationPrincipal AdminPrincipal a, @RequestBody JsonNode b) {
        require(a, "return:policy-manage");
        return ResponseEntity.status(HttpStatus.CREATED).body(policies.create(a, b.path("storeId").asText("*"), b.path("kind").asText("RETAIL_RETURN"), b.path("name").asText(null), b.get("config")));
    }

    @GetMapping("/policies/{id}")
    public Map<String, Object> policy(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "return:view"); return policies.policy(a, id); }

    @GetMapping("/policies/{id}/versions")
    public List<Map<String, Object>> policyVersions(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "return:view"); return policies.versions(a, id); }

    @PutMapping("/policies/{id}/draft")
    public Map<String, Object> policyDraft(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody JsonNode b) {
        require(a, "return:policy-manage");
        return policies.saveDraft(a, id, b.get("config"), b.path("note").asText(null));
    }

    @PostMapping("/policies/{id}/versions/{versionId}/submit")
    public Map<String, Object> policySubmit(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @PathVariable long versionId) { require(a, "return:policy-manage"); return policies.submit(a, id, versionId); }

    @PostMapping("/policies/{id}/versions/{versionId}/decision")
    public Map<String, Object> policyDecide(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @PathVariable long versionId, @RequestBody Map<String, Object> b) {
        require(a, "return:policy-approve");
        return policies.decide(a, id, versionId, Boolean.TRUE.equals(b.get("approve")), text(b, "note"));
    }

    @PostMapping("/policies/{id}/versions/{versionId}/publish")
    public Map<String, Object> policyPublish(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @PathVariable long versionId) { require(a, "return:policy-approve"); return policies.publish(a, id, versionId); }

    @PostMapping("/policies/{id}/versions/{versionId}/rollback")
    public Map<String, Object> policyRollback(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @PathVariable long versionId, @RequestBody(required = false) Map<String, Object> b) {
        require(a, "return:policy-approve");
        return policies.rollback(a, id, versionId, text(b, "note"));
    }

    @PostMapping("/policies/{id}/versions/{versionId}/withdraw")
    public Map<String, Object> policyWithdraw(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @PathVariable long versionId) { require(a, "return:policy-manage"); return policies.withdraw(a, id, versionId); }

    // ---- refund configuration ---------------------------------------------------------------------------------------------

    @GetMapping("/settings")
    public Map<String, Object> getSettings(@AuthenticationPrincipal AdminPrincipal a) { require(a, "return:view"); return settings.view(a.tenantId()); }

    @PutMapping("/settings")
    public Map<String, Object> putSettings(@AuthenticationPrincipal AdminPrincipal a, @RequestBody Map<String, Object> b) { require(a, "return:config"); return settings.upsert(a, b); }
}

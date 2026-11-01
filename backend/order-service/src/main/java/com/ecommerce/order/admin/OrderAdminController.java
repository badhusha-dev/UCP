package com.ecommerce.order.admin;

import com.ecommerce.order.admin.security.AdminPrincipal;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static com.ecommerce.order.admin.AdminSupport.require;

/**
 * Order Management Control Tower API. Authenticated by AdminAuthenticationFilter (verified JWT, live tenant grant); every endpoint
 * also enforces its own permission, and every query is scoped to the verified tenant and the caller's stores.
 */
@RestController
@RequestMapping("/api/orders/admin")
public class OrderAdminController {
    private final OrderQueryService query;
    private final OrderAdminService admin;
    private final FulfillmentService fulfillment;
    private final ExceptionService exceptions;
    private final BulkService bulk;
    private final OrderReportService reports;
    private final OrderSettingsService settings;
    private final OrderMiscService misc;

    public OrderAdminController(OrderQueryService query, OrderAdminService admin, FulfillmentService fulfillment, ExceptionService exceptions, BulkService bulk,
                                OrderReportService reports, OrderSettingsService settings, OrderMiscService misc) {
        this.query = query; this.admin = admin; this.fulfillment = fulfillment; this.exceptions = exceptions; this.bulk = bulk; this.reports = reports; this.settings = settings; this.misc = misc;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> conflict(OptimisticLockingFailureException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "The order was changed by someone else; reload and try again.")); }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }

    private static String correlation(String header) { return header != null && header.matches("[A-Za-z0-9_.:-]{8,64}") ? header : UUID.randomUUID().toString(); }

    private static OrderQueryService.Filter filter(String q, String view, List<String> status, String orderType, List<String> storeId, String vendorId, String paymentStatus,
                                                   String fulfillmentStatus, String deliveryStatus, LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount,
                                                   Long operatorId, Boolean slaBreached) {
        return new OrderQueryService.Filter(q, view, status, orderType, storeId, vendorId, paymentStatus, fulfillmentStatus, deliveryStatus, from, to, minAmount, maxAmount, operatorId, slaBreached, null);
    }

    // ---- meta, dashboard, lists --------------------------------------------------------------------------------------
    @GetMapping("/meta")
    public Map<String, Object> meta(@AuthenticationPrincipal AdminPrincipal a) {
        require(a, "order:view");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("views", OrderStateMachine.VIEWS.keySet());
        m.put("retailStatuses", OrderStateMachine.known("RETAIL")); m.put("restaurantStatuses", OrderStateMachine.known("RESTAURANT"));
        m.put("retailFlow", OrderStateMachine.flow("RETAIL")); m.put("restaurantFlow", OrderStateMachine.flow("RESTAURANT"));
        m.put("permissions", new TreeSet<>(a.permissions()));
        m.put("canViewCustomer", a.hasPermission("order:customer:view")); m.put("canExport", a.hasPermission("order:export"));
        m.put("bulkActions", BulkService.ACTIONS);
        return m;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(required = false) List<String> storeId, @RequestParam(required = false) String vendorId,
                                         @RequestParam(required = false) String orderType, @RequestParam(required = false) String paymentStatus, @RequestParam(required = false) String fulfillmentStatus,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        require(a, "order:view");
        return reports.dashboard(a, filter(null, null, null, orderType, storeId, vendorId, paymentStatus, fulfillmentStatus, null, from, to, null, null, null, null));
    }

    @GetMapping("/orders")
    public Map<String, Object> list(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(required = false) String q, @RequestParam(required = false) String view,
                                    @RequestParam(required = false) List<String> status, @RequestParam(required = false) String orderType, @RequestParam(required = false) List<String> storeId,
                                    @RequestParam(required = false) String vendorId, @RequestParam(required = false) String paymentStatus, @RequestParam(required = false) String fulfillmentStatus,
                                    @RequestParam(required = false) String deliveryStatus, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) BigDecimal minAmount,
                                    @RequestParam(required = false) BigDecimal maxAmount, @RequestParam(required = false) Long operatorId, @RequestParam(required = false) Boolean slaBreached,
                                    @RequestParam(required = false) List<String> sort, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        require(a, "order:view");
        return query.search(a, filter(q, view, status, orderType, storeId, vendorId, paymentStatus, fulfillmentStatus, deliveryStatus, from, to, minAmount, maxAmount, operatorId, slaBreached), sort, page, size);
    }

    @GetMapping("/view-counts")
    public Map<String, Long> viewCounts(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(required = false) List<String> storeId, @RequestParam(required = false) String orderType) {
        require(a, "order:view");
        return query.viewCounts(a, filter(null, null, null, orderType, storeId, null, null, null, null, null, null, null, null, null, null));
    }

    @GetMapping(value = "/orders/export", produces = "text/csv")
    public ResponseEntity<String> export(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(required = false) String q, @RequestParam(required = false) String view,
                                         @RequestParam(required = false) List<String> status, @RequestParam(required = false) String orderType, @RequestParam(required = false) List<String> storeId,
                                         @RequestParam(required = false) String paymentStatus, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) List<String> sort) {
        require(a, "order:export");
        String csv = query.exportCsv(a, filter(q, view, status, orderType, storeId, null, paymentStatus, null, null, from, to, null, null, null, null), sort);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orders.csv\"").body(csv);
    }

    // ---- one order ------------------------------------------------------------------------------------------------------
    @GetMapping("/orders/{id}")
    public Map<String, Object> details(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "order:view"); return admin.details(a, id); }

    @GetMapping("/orders/{id}/timeline")
    public Map<String, Object> timeline(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "order:view"); return admin.timeline(a, id); }

    public record TransitionRequest(String to, String reason, Long expectedVersion) {}
    public record ReasonRequest(String reason) {}
    public record NoteRequest(String kind, String body) {}
    public record AssignRequest(Long operatorId) {}
    public record RestaurantRequest(Integer prepMinutes, String reason) {}

    @PostMapping("/orders/{id}/transition")
    public Map<String, Object> transition(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody TransitionRequest r,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "X-Correlation-Id", required = false) String corr) {
        require(a, "order:view");
        return admin.transition(a, id, r.to(), r.reason(), r.expectedVersion(), key, correlation(corr), null);
    }

    @PostMapping("/orders/{id}/hold")
    public Map<String, Object> hold(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody ReasonRequest r,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "X-Correlation-Id", required = false) String corr) {
        return admin.transition(a, id, "ON_HOLD", r.reason(), null, key, correlation(corr), null);
    }

    @PostMapping("/orders/{id}/release")
    public Map<String, Object> release(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody(required = false) ReasonRequest r,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "X-Correlation-Id", required = false) String corr) {
        return admin.release(a, id, r == null ? null : r.reason(), key, correlation(corr));
    }

    @PostMapping("/orders/{id}/request-cancellation")
    public Map<String, Object> requestCancellation(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody ReasonRequest r,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "X-Correlation-Id", required = false) String corr) {
        return admin.transition(a, id, "CANCELLATION_REQUESTED", r.reason(), null, key, correlation(corr), null);
    }

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancel(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody ReasonRequest r,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "X-Correlation-Id", required = false) String corr) {
        return admin.transition(a, id, "CANCELLED", r.reason(), null, key, correlation(corr), null);
    }

    @PostMapping("/orders/{id}/restaurant/{action}")
    public Map<String, Object> restaurant(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @PathVariable String action, @RequestBody(required = false) RestaurantRequest r,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "X-Correlation-Id", required = false) String corr) {
        return admin.restaurantAction(a, id, action, r == null ? null : r.prepMinutes(), r == null ? null : r.reason(), key, correlation(corr));
    }

    @PostMapping("/orders/{id}/notes")
    public Map<String, Object> addNote(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody NoteRequest r) { return admin.addNote(a, id, r.kind(), r.body()); }

    @GetMapping("/orders/{id}/notes")
    public List<Map<String, Object>> notes(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "order:view"); admin.load(a, id, false); return admin.notes(a, id); }

    @PostMapping("/orders/{id}/assign")
    public Map<String, Object> assign(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody AssignRequest r) { return admin.assignOperator(a, id, r.operatorId()); }

    // ---- fulfillment ----------------------------------------------------------------------------------------------------
    @GetMapping("/orders/{id}/fulfillment")
    public Map<String, Object> fulfillmentOverview(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { return fulfillment.overview(a, id); }

    @PostMapping("/orders/{id}/fulfillments")
    public ResponseEntity<Map<String, Object>> createFulfillment(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody FulfillmentService.Request r,
                                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "X-Correlation-Id", required = false) String corr) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fulfillment.create(a, id, r, key, correlation(corr)));
    }

    public record PackingRequest(String packingStatus) {}
    public record ShipRequest(String carrier, String trackingReference) {}

    @PostMapping("/fulfillments/{fid}/packing")
    public Map<String, Object> packing(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long fid, @RequestBody PackingRequest r) { return fulfillment.packing(a, fid, r.packingStatus()); }

    @PostMapping("/fulfillments/{fid}/ship")
    public Map<String, Object> ship(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long fid, @RequestBody ShipRequest r,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestHeader(value = "X-Correlation-Id", required = false) String corr) {
        return fulfillment.ship(a, fid, r.carrier(), r.trackingReference(), key, correlation(corr));
    }

    @PostMapping("/fulfillments/{fid}/cancel")
    public Map<String, Object> cancelFulfillment(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long fid) { return fulfillment.cancel(a, fid); }

    // ---- exceptions ------------------------------------------------------------------------------------------------------
    public record AssignExceptionRequest(Long assigneeId, String note) {}
    public record NoteOnlyRequest(String note) {}

    @GetMapping("/exceptions")
    public Map<String, Object> exceptions(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String priority, @RequestParam(required = false) Long assigneeId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        require(a, "order:view");
        Map<String, Object> out = new LinkedHashMap<>(exceptions.list(a, status, category, priority, assigneeId, page, size));
        out.put("summary", exceptions.summary(a));
        return out;
    }

    @PostMapping("/exceptions/scan")
    public Map<String, Object> scan(@AuthenticationPrincipal AdminPrincipal a) { require(a, "order:exception:manage"); return exceptions.scan(a.tenantId()); }

    @GetMapping("/exceptions/{id}")
    public Map<String, Object> exception(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "order:view"); return exceptions.get(a, id); }

    @PostMapping("/exceptions/{id}/assign")
    public Map<String, Object> assignException(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody AssignExceptionRequest r) { return exceptions.assign(a, id, r.assigneeId(), r.note()); }

    @PostMapping("/exceptions/{id}/escalate")
    public Map<String, Object> escalate(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody NoteOnlyRequest r) { return exceptions.escalate(a, id, r.note()); }

    @PostMapping("/exceptions/{id}/resolve")
    public Map<String, Object> resolve(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody NoteOnlyRequest r) { return exceptions.resolve(a, id, false, r.note()); }

    @PostMapping("/exceptions/{id}/dismiss")
    public Map<String, Object> dismiss(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody NoteOnlyRequest r) { return exceptions.resolve(a, id, true, r.note()); }

    @PostMapping("/exceptions/{id}/notes")
    public Map<String, Object> exceptionNote(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id, @RequestBody NoteOnlyRequest r) { return exceptions.addNote(a, id, r.note()); }

    // ---- bulk ------------------------------------------------------------------------------------------------------------
    public record BulkRequest(String action, List<Long> orderIds, Map<String, Object> params) {}

    @PostMapping("/bulk/preview")
    public Map<String, Object> bulkPreview(@AuthenticationPrincipal AdminPrincipal a, @RequestBody BulkRequest r) { return bulk.preview(a, r.action(), r.orderIds(), r.params()); }

    @PostMapping("/bulk/{jobId}/execute")
    public Map<String, Object> bulkExecute(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long jobId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @RequestHeader(value = "X-Correlation-Id", required = false) String corr) { return bulk.execute(a, jobId, key, correlation(corr)); }

    @GetMapping("/bulk/{jobId}")
    public Map<String, Object> bulkJob(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long jobId) { require(a, "order:bulk"); return bulk.job(a, jobId); }

    // ---- views, settings, returns, reports, audit --------------------------------------------------------------------------
    public record ViewRequest(String name, Map<String, Object> filters) {}

    @GetMapping("/saved-views")
    public List<Map<String, Object>> views(@AuthenticationPrincipal AdminPrincipal a) { require(a, "order:view"); return misc.views(a); }

    @PostMapping("/saved-views")
    public Map<String, Object> saveView(@AuthenticationPrincipal AdminPrincipal a, @RequestBody ViewRequest r) { require(a, "order:view"); return misc.saveView(a, r.name(), r.filters()); }

    @DeleteMapping("/saved-views/{id}")
    public ResponseEntity<Void> deleteView(@AuthenticationPrincipal AdminPrincipal a, @PathVariable long id) { require(a, "order:view"); misc.deleteView(a, id); return ResponseEntity.noContent().build(); }

    @GetMapping("/settings")
    public Map<String, Object> getSettings(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(required = false) String storeId) { require(a, "order:view"); return settings.view(a.tenantId(), storeId); }

    @PutMapping("/settings")
    public Map<String, Object> putSettings(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(required = false) String storeId, @RequestBody Map<String, Object> body) {
        require(a, "order:settings"); return settings.upsert(a, storeId, body);
    }

    @GetMapping("/returns")
    public Map<String, Object> returns(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        require(a, "order:view"); return misc.returns(a, status, page, size);
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> reportSummary(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(defaultValue = "day") String groupBy, @RequestParam(required = false) List<String> storeId,
                                             @RequestParam(required = false) String orderType, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        require(a, "order:report:view");
        return reports.summary(a, filter(null, null, null, orderType, storeId, null, null, null, null, from, to, null, null, null, null), groupBy);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@AuthenticationPrincipal AdminPrincipal a, @RequestParam(required = false) Long orderId, @RequestParam(required = false) String action, @RequestParam(defaultValue = "200") int limit) {
        require(a, "order:audit:view"); return admin.audit(a, orderId, action, limit);
    }
}

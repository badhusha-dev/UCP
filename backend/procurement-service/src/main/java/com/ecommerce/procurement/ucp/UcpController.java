package com.ecommerce.procurement.ucp;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Tenant-scoped procurement API. The filter verifies the token; each route then demands an explicit permission. */
@RestController
@RequestMapping("/api/procurement/ucp")
@RequiredArgsConstructor
class UcpController {
    private final UcpService svc;

    private static UcpPrincipal need(UcpPrincipal p, String... anyOf) {
        if (p == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        for (String k : anyOf) if (p.has(k)) return p;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assigned permission is required: " + anyOf[0]);
    }

    // suppliers
    @GetMapping("/suppliers")
    List<Map<String, Object>> suppliers(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        return svc.suppliers(need(p, "supplier:view").tenantId(), status, q);
    }

    @PostMapping("/suppliers")
    Map<String, Object> createSupplier(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @RequestBody Map<String, Object> body) {
        return svc.createSupplier(need(p, "supplier:manage").tenantId(), body, p.email());
    }

    @PostMapping("/suppliers/{id}/{action}")
    Map<String, Object> supplierAction(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id, @PathVariable String action) {
        String status = switch (action) { case "approve" -> "APPROVED"; case "suspend" -> "SUSPENDED"; case "archive" -> "ARCHIVED"; default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown action."); };
        return svc.setSupplierStatus(need(p, "supplier:manage").tenantId(), id, status, p.email());
    }

    @GetMapping("/suppliers/{id}/items")
    List<Map<String, Object>> items(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id) { return svc.items(need(p, "supplier:view").tenantId(), id); }

    @PostMapping("/suppliers/{id}/items")
    Map<String, Object> upsertItem(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id, @RequestBody Map<String, Object> body) {
        return svc.upsertItem(need(p, "supplier:manage").tenantId(), id, body, p.email());
    }

    @GetMapping("/suppliers/{id}/performance")
    Map<String, Object> performance(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id) { return svc.performance(need(p, "supplier:view").tenantId(), id); }

    // purchase orders
    @GetMapping("/purchase-orders")
    List<Map<String, Object>> pos(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @RequestParam(required = false) String status, @RequestParam(required = false) Long supplierId) {
        return svc.pos(need(p, "purchase-order:view").tenantId(), status, supplierId);
    }

    @GetMapping("/purchase-orders/{id}")
    Map<String, Object> po(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id) { return svc.po(need(p, "purchase-order:view").tenantId(), id); }

    @PostMapping("/purchase-orders")
    Map<String, Object> createPo(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @RequestBody Map<String, Object> body) {
        return svc.createPo(need(p, "purchase-order:manage").tenantId(), body, p.email());
    }

    @PostMapping("/purchase-orders/{id}/{action}")
    Map<String, Object> poAction(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id, @PathVariable String action, @RequestBody(required = false) Map<String, Object> body) {
        PoRules.State target = switch (action) {
            case "submit" -> PoRules.State.PENDING_APPROVAL; case "approve" -> PoRules.State.APPROVED; case "reject" -> PoRules.State.REJECTED;
            case "issue" -> PoRules.State.ISSUED; case "cancel" -> PoRules.State.CANCELLED; case "reopen" -> PoRules.State.DRAFT;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown action.");
        };
        need(p, target == PoRules.State.APPROVED || target == PoRules.State.REJECTED ? "purchase-order:approve" : "purchase-order:manage");
        return svc.transitionPo(p.tenantId(), id, target, p.email(), body == null ? null : (String) body.get("reason"));
    }

    @GetMapping(value = "/purchase-orders/{id}/document", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> document(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"purchase-order-" + id + ".html\"").body(svc.poDocument(need(p, "purchase-order:view").tenantId(), id));
    }

    // goods receiving
    @GetMapping("/receipts")
    List<Map<String, Object>> receipts(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p) { return svc.receipts(need(p, "purchase-order:view", "goods-receipt:manage").tenantId()); }

    @SuppressWarnings("unchecked")
    @PostMapping("/purchase-orders/{id}/receipts")
    Map<String, Object> receive(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody Map<String, Object> body) {
        return svc.receive(need(p, "goods-receipt:manage").tenantId(), id, key, (List<Map<String, Object>>) body.get("lines"), p.email());
    }

    // supplier returns
    @GetMapping("/supplier-returns")
    List<Map<String, Object>> returns(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p) { return svc.returns(need(p, "supplier-return:manage", "purchase-order:view").tenantId()); }

    @PostMapping("/supplier-returns")
    Map<String, Object> createReturn(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @RequestBody Map<String, Object> body) {
        return svc.createReturn(need(p, "supplier-return:manage").tenantId(), body, p.email());
    }

    @PostMapping("/supplier-returns/{id}/ship")
    Map<String, Object> ship(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id) { return svc.shipReturn(need(p, "supplier-return:manage").tenantId(), id, p.email()); }

    @PostMapping("/supplier-returns/{id}/credit")
    Map<String, Object> credit(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @PathVariable long id) { return svc.creditReturn(need(p, "supplier-return:manage").tenantId(), id, p.email()); }

    // replenishment & analytics
    @GetMapping("/replenishment")
    List<Map<String, Object>> replenishment(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p) { return svc.replenishment(need(p, "purchase-order:view").tenantId()); }

    /** Converts a suggestion into a DRAFT purchase order only; submission and approval stay manual. */
    @PostMapping("/replenishment/draft-po")
    Map<String, Object> draftFromSuggestion(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p, @RequestBody Map<String, Object> body) {
        return svc.createPo(need(p, "purchase-order:manage").tenantId(), body, p.email());
    }

    @GetMapping("/analytics")
    Map<String, Object> analytics(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p) { return svc.analytics(need(p, "purchase-order:view").tenantId()); }

    @GetMapping("/audit")
    List<Map<String, Object>> audit(@RequestAttribute(UcpAuthenticationFilter.ATTR) UcpPrincipal p) { return svc.audit(need(p, "inventory:audit").tenantId()); }
}

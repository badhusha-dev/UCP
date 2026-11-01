package com.ecommerce.inventory.ops;

import com.ecommerce.inventory.ops.domain.TransferState;
import com.ecommerce.inventory.security.InventoryPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Tenant-scoped admin API. Tenant comes only from the verified token principal; every route enforces an explicit permission. */
@RestController
@RequestMapping("/api/inventory/ops")
@RequiredArgsConstructor
public class OpsController {
    private final OpsService ops;

    private static InventoryPrincipal need(InventoryPrincipal p, String... anyOf) {
        if (p == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        for (String k : anyOf) if (p.hasPermission(k)) return p;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assigned Inventory permission is required: " + anyOf[0]);
    }

    // dashboard, lists, reports
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal InventoryPrincipal p, @RequestParam(required = false) Long warehouseId) {
        return ops.dashboard(need(p, "inventory:view").tenantId(), warehouseId);
    }

    @GetMapping("/items")
    public Map<String, Object> items(@AuthenticationPrincipal InventoryPrincipal p, @RequestParam(required = false) String q, @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String category, @RequestParam(required = false) String sort,
                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ops.inventoryPage(need(p, "inventory:view").tenantId(), q, status, category, sort, page, size);
    }

    @GetMapping("/valuation")
    public Map<String, Object> valuation(@AuthenticationPrincipal InventoryPrincipal p) { return ops.valuation(need(p, "inventory:view").tenantId()); }

    @GetMapping("/forecast")
    public List<Map<String, Object>> forecast(@AuthenticationPrincipal InventoryPrincipal p, @RequestParam(defaultValue = "30") int windowDays) {
        return ops.forecast(need(p, "inventory:view").tenantId(), windowDays);
    }

    @GetMapping("/reservations/reconciliation")
    public List<Map<String, Object>> reconciliation(@AuthenticationPrincipal InventoryPrincipal p) { return ops.reservationReconciliation(need(p, "inventory:view").tenantId()); }

    @GetMapping("/audit")
    public List<OpsAudit> audit(@AuthenticationPrincipal InventoryPrincipal p) { return ops.auditTrail(need(p, "inventory:audit").tenantId()); }

    @GetMapping("/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal InventoryPrincipal p) {
        var page = ops.inventoryPage(need(p, "inventory:export").tenantId(), null, null, null, "sku", 0, 200);
        StringBuilder csv = new StringBuilder("productId,sku,onHand,reserved,unavailable,incoming,available,reorderPoint,status\n");
        for (Object o : (List<?>) page.get("content")) {
            Map<?, ?> m = (Map<?, ?>) o;
            csv.append(m.get("productId")).append(',').append(csvCell(m.get("sku"))).append(',').append(m.get("onHand")).append(',').append(m.get("reserved")).append(',')
                    .append(m.get("unavailable")).append(',').append(m.get("incoming")).append(',').append(m.get("available")).append(',').append(m.get("reorderPoint")).append(',').append(m.get("status")).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory.csv\"").header(HttpHeaders.CONTENT_TYPE, "text/csv").body(csv.toString());
    }

    /** Neutralises spreadsheet formula injection. */
    private static String csvCell(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    // warehouses
    @GetMapping("/warehouses")
    public List<Warehouse> warehouses(@AuthenticationPrincipal InventoryPrincipal p) { return ops.listWarehouses(need(p, "warehouse:view").tenantId()); }

    @PostMapping("/warehouses")
    public Warehouse createWarehouse(@AuthenticationPrincipal InventoryPrincipal p, @RequestBody OpsService.WarehouseRequest r) {
        return ops.createWarehouse(need(p, "warehouse:manage").tenantId(), r, p.email());
    }

    @GetMapping("/warehouses/{id}/locations")
    public List<WarehouseLocation> locations(@AuthenticationPrincipal InventoryPrincipal p, @PathVariable Long id) { return ops.listLocations(need(p, "warehouse:view").tenantId(), id); }

    @PostMapping("/warehouses/{id}/locations")
    public WarehouseLocation addLocation(@AuthenticationPrincipal InventoryPrincipal p, @PathVariable Long id, @RequestBody OpsService.LocationRequest r) {
        return ops.addLocation(need(p, "warehouse:manage").tenantId(), id, r, p.email());
    }

    @GetMapping("/warehouse-stock")
    public List<WarehouseStock> warehouseStock(@AuthenticationPrincipal InventoryPrincipal p, @RequestParam(required = false) Long warehouseId) {
        return ops.warehouseStock(need(p, "warehouse:view", "inventory:view").tenantId(), warehouseId);
    }

    // transfers
    @GetMapping("/transfers")
    public List<Map<String, Object>> transfers(@AuthenticationPrincipal InventoryPrincipal p) { return ops.listTransfers(need(p, "inventory:view").tenantId()); }

    @PostMapping("/transfers")
    public Map<String, Object> createTransfer(@AuthenticationPrincipal InventoryPrincipal p, @RequestBody OpsService.TransferRequest r) {
        return ops.createTransfer(need(p, "inventory:transfer").tenantId(), r, p.email());
    }

    /** Body: {"receive":[{"productId":1,"received":5,"damaged":1}]} is only used for the RECEIVED step. */
    @PostMapping("/transfers/{id}/{action}")
    public Map<String, Object> transfer(@AuthenticationPrincipal InventoryPrincipal p, @PathVariable Long id, @PathVariable String action,
                                        @RequestBody(required = false) Map<String, Object> body) {
        TransferState target = switch (action) {
            case "request" -> TransferState.REQUESTED; case "approve" -> TransferState.APPROVED; case "reject" -> TransferState.REJECTED;
            case "pick" -> TransferState.PICKING; case "dispatch" -> TransferState.DISPATCHED; case "in-transit" -> TransferState.IN_TRANSIT;
            case "receive" -> TransferState.RECEIVED; case "cancel" -> TransferState.CANCELLED; case "lost" -> TransferState.LOST; case "damaged" -> TransferState.DAMAGED;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transfer action.");
        };
        boolean approverAction = target == TransferState.APPROVED || target == TransferState.REJECTED;
        need(p, approverAction ? "inventory:transfer-approve" : "inventory:transfer");
        List<OpsService.ReceiveLine> receive = null;
        if (body != null && body.get("receive") instanceof List<?> list) {
            receive = list.stream().map(o -> (Map<?, ?>) o).map(m -> new OpsService.ReceiveLine(
                    ((Number) m.get("productId")).longValue(), m.get("received") == null ? null : ((Number) m.get("received")).intValue(),
                    m.get("damaged") == null ? null : ((Number) m.get("damaged")).intValue())).toList();
        }
        return ops.transition(p.tenantId(), id, target, p.email(), p.hasPermission("inventory:transfer-approve"), receive);
    }

    // adjustments
    @GetMapping("/adjustments")
    public List<StockAdjustment> adjustments(@AuthenticationPrincipal InventoryPrincipal p) { return ops.listAdjustments(need(p, "inventory:view").tenantId()); }

    @PostMapping("/adjustments")
    public StockAdjustment adjust(@AuthenticationPrincipal InventoryPrincipal p, @RequestBody OpsService.AdjustmentRequest r) {
        return ops.requestAdjustment(need(p, "inventory:adjust").tenantId(), r, p.email(), p.hasPermission("inventory:adjust-approve"));
    }

    @PostMapping("/adjustments/{id}/approve")
    public StockAdjustment approve(@AuthenticationPrincipal InventoryPrincipal p, @PathVariable Long id) {
        return ops.decideAdjustment(need(p, "inventory:adjust-approve").tenantId(), id, true, p.email());
    }

    @PostMapping("/adjustments/{id}/reject")
    public StockAdjustment reject(@AuthenticationPrincipal InventoryPrincipal p, @PathVariable Long id) {
        return ops.decideAdjustment(need(p, "inventory:adjust-approve").tenantId(), id, false, p.email());
    }

    // configuration
    @GetMapping("/config")
    public InventoryConfig config(@AuthenticationPrincipal InventoryPrincipal p) { return ops.getOrCreateConfig(need(p, "inventory:view").tenantId()); }

    @PutMapping("/config")
    public InventoryConfig updateConfig(@AuthenticationPrincipal InventoryPrincipal p, @RequestBody Map<String, Object> body) {
        return ops.updateConfig(need(p, "inventory:config").tenantId(), body, p.email());
    }
}

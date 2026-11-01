package com.ecommerce.stock.controller;

import com.ecommerce.stock.dto.*;
import com.ecommerce.stock.model.StockMovement;
import com.ecommerce.stock.model.StockReservation;
import com.ecommerce.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/stock/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stock Administration", description = "Privileged staff operations for manual adjustments, stock movements, and CSV import/export")
@SecurityRequirement(name = "bearerAuth")
public class StockAdminController {

    private final StockService inventoryService;

    @Operation(summary = "List All Inventory Items", description = "Authoritative stock catalog for all active SKUs (Manager/Admin only).")
    @GetMapping("/items")
    public ResponseEntity<List<StockItemDto>> getAllItems(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(inventoryService.getAllItems());
    }

    @Operation(summary = "Adjust Stock Quantity", description = "Manual warehouse inventory adjustments with audit trail reason (Manager/Admin only).")
    @PostMapping({"/adjust", "/adjustments"})
    public ResponseEntity<StockItemDto> adjustStock(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @Valid @RequestBody StockAdjustmentRequest request) {
        assertManagerOrAdmin(role);
        try {
            return ResponseEntity.ok(inventoryService.adjustStock(request, email != null ? email : "STAFF"));
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent stock update — please retry.");
        }
    }

    @Operation(summary = "List Low-Stock Alerts", description = "Items where available stock is below or equal to reorder threshold (Manager/Admin only).")
    @GetMapping("/alerts/low-stock")
    public ResponseEntity<List<StockItemDto>> getLowStockAlerts(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(inventoryService.getLowStockAlerts());
    }

    @Operation(summary = "View Active Holds & Reservations", description = "Lists current checkout stock holds (Manager/Admin only).")
    @GetMapping("/reservations")
    public ResponseEntity<List<StockReservation>> getActiveReservations(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(inventoryService.getActiveReservations());
    }

    @Operation(summary = "View Recent Stock Movements", description = "Audit trail of warehouse stock changes (Manager/Admin only).")
    @GetMapping("/movements")
    public ResponseEntity<List<StockMovement>> getRecentMovements(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(inventoryService.getRecentMovements());
    }

    @Operation(summary = "Get Inventory Analytics Summary", description = "Warehouse asset valuation, unit totals, and alert statistics (Manager/Admin only).")
    @GetMapping({"/analytics", "/analytics/summary"})
    public ResponseEntity<StockAnalyticsSummaryDto> getAnalyticsSummary(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        return ResponseEntity.ok(inventoryService.getAnalyticsSummary());
    }

    @Operation(summary = "Bulk CSV Stock Import", description = "Upload spreadsheet for bulk inventory synchronization (Manager/Admin only).")
    @PostMapping(value = {"/import", "/import/csv"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportResultDto> importCsv(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestParam("file") MultipartFile file) {
        assertManagerOrAdmin(role);
        try {
            return ResponseEntity.ok(inventoryService.importCsv(file.getBytes(), email != null ? email : "STAFF"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File upload failed: " + e.getMessage());
        }
    }

    @Operation(summary = "Export Inventory to RFC-4180 CSV", description = "Streams a spreadsheet-safe CSV report of all warehouse stock (Manager/Admin only).")
    @GetMapping(value = {"/export", "/export/csv"}, produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        assertManagerOrAdmin(role);
        String csv = inventoryService.exportCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory_report_" + System.currentTimeMillis() + ".csv\"")
                .body(csv);
    }

    private void assertManagerOrAdmin(String role) {
        if (role == null || (!role.toUpperCase().contains("ADMIN") && !role.toUpperCase().contains("MANAGER"))) {
            log.warn("Security Alert: Unauthorized access attempt to Inventory Admin by role: {}", role);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Staff authorization (Manager or Admin) required.");
        }
    }
}

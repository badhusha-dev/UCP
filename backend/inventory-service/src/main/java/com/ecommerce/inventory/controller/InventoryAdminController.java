package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.CsvImportResultDto;
import com.ecommerce.inventory.dto.InventoryItemDto;
import com.ecommerce.inventory.dto.InventoryMetadataRequest;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.inventory.security.InventoryPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory Catalog", description = "SKU, warehouse location, cost and reorder configuration")
@SecurityRequirement(name = "bearerAuth")
public class InventoryAdminController {

    private final InventoryService inventoryService;

    @Operation(summary = "List inventory catalog metadata", description = "Returns SKU, warehouse, cost and reorder configuration for manager/admin users.")
    @GetMapping("/items")
    public ResponseEntity<List<InventoryItemDto>> getAllItems(
            @AuthenticationPrincipal InventoryPrincipal principal) {
        requirePermission(principal, "inventory:read");
        return ResponseEntity.ok(inventoryService.getAllItems(principal.tenantId()));
    }

    @Operation(summary = "Create inventory metadata", description = "Creates SKU, warehouse, cost and reorder settings without changing stock quantity.")
    @PostMapping("/items")
    public ResponseEntity<InventoryItemDto> createItem(
            @AuthenticationPrincipal InventoryPrincipal principal,
            @Valid @RequestBody InventoryMetadataRequest request) {
        requirePermission(principal, "inventory:manage");
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createMetadata(principal.tenantId(), request, principal.email()));
    }

    @Operation(summary = "Update inventory metadata", description = "Updates SKU, warehouse, cost and reorder settings; stock remains managed in Stock.")
    @PutMapping("/items/{productId}")
    public ResponseEntity<InventoryItemDto> updateItem(
            @AuthenticationPrincipal InventoryPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody InventoryMetadataRequest request) {
        requirePermission(principal, "inventory:manage");
        return ResponseEntity.ok(inventoryService.updateMetadata(principal.tenantId(), productId, request, principal.email()));
    }

    @Operation(summary = "Archive inventory metadata", description = "Soft archives a metadata record only after its physical and reserved stock reach zero.")
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> archiveItem(
            @AuthenticationPrincipal InventoryPrincipal principal,
            @PathVariable Long productId) {
        requirePermission(principal, "inventory:manage");
        inventoryService.archiveMetadata(principal.tenantId(), productId, principal.email());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restore archived inventory metadata")
    @PatchMapping("/items/{productId}/restore")
    public ResponseEntity<InventoryItemDto> restoreItem(
            @AuthenticationPrincipal InventoryPrincipal principal,
            @PathVariable Long productId) {
        requirePermission(principal, "inventory:manage");
        return ResponseEntity.ok(inventoryService.restoreMetadata(principal.tenantId(), productId, principal.email()));
    }

    @Operation(summary = "Import inventory catalog metadata from CSV")
    @PostMapping(value = {"/import", "/import/csv"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportResultDto> importCsv(
            @AuthenticationPrincipal InventoryPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        requirePermission(principal, "inventory:manage");
        try {
            return ResponseEntity.ok(inventoryService.importCsv(principal.tenantId(), file.getBytes(), principal.email()));
        } catch (Exception e) {
            log.warn("Inventory metadata CSV import failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File upload failed: " + e.getMessage());
        }
    }

    @Operation(summary = "Export inventory catalog metadata as CSV")
    @GetMapping(value = {"/export", "/export/csv"}, produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @AuthenticationPrincipal InventoryPrincipal principal) {
        requirePermission(principal, "inventory:read");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory_catalog_" + System.currentTimeMillis() + ".csv\"")
                .body(inventoryService.exportCsv(principal.tenantId()));
    }

    @GetMapping("/analytics")
    public ResponseEntity<com.ecommerce.inventory.dto.InventoryAnalyticsSummaryDto> analytics(
            @AuthenticationPrincipal InventoryPrincipal principal) {
        requirePermission(principal, "inventory:read");
        return ResponseEntity.ok(inventoryService.getAnalyticsSummary(principal.tenantId()));
    }

    @GetMapping("/movements")
    public ResponseEntity<List<com.ecommerce.inventory.model.StockMovement>> movements(
            @AuthenticationPrincipal InventoryPrincipal principal, @RequestParam(required = false) Long productId) {
        requirePermission(principal, "inventory:read");
        return ResponseEntity.ok(productId == null ? inventoryService.getRecentMovements(principal.tenantId())
                : inventoryService.getMovements(principal.tenantId(), productId));
    }

    @GetMapping("/reservations")
    public ResponseEntity<List<com.ecommerce.inventory.model.StockReservation>> reservations(
            @AuthenticationPrincipal InventoryPrincipal principal) {
        requirePermission(principal, "inventory:read");
        return ResponseEntity.ok(inventoryService.getActiveReservations(principal.tenantId()));
    }

    @PostMapping("/adjust")
    public ResponseEntity<InventoryItemDto> adjust(
            @AuthenticationPrincipal InventoryPrincipal principal, @Valid @RequestBody com.ecommerce.inventory.dto.StockAdjustmentRequest request) {
        requirePermission(principal, "inventory:manage");
        return ResponseEntity.ok(inventoryService.adjustStock(principal.tenantId(), request, principal.email()));
    }

    private void requirePermission(InventoryPrincipal principal, String permission) {
        if (principal == null || !principal.hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assigned Inventory permission is required.");
        }
    }
}

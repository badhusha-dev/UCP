package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.InventoryItemDto;
import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.dto.StockReservationResponse;
import com.ecommerce.inventory.security.InventoryPrincipal;
import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryItemDto> get(@AuthenticationPrincipal InventoryPrincipal principal,
                                                @PathVariable Long productId) {
        requirePermission(principal, "inventory:read");
        return ResponseEntity.ok(inventoryService.getInventory(principal.tenantId(), productId));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<InventoryItemDto>> batch(@AuthenticationPrincipal InventoryPrincipal principal,
                                                        @RequestBody List<Long> productIds) {
        requirePermission(principal, "inventory:read");
        return ResponseEntity.ok(inventoryService.getBatchInventory(principal.tenantId(), productIds));
    }

    @PostMapping("/reservations")
    public ResponseEntity<StockReservationResponse> reserve(@AuthenticationPrincipal InventoryPrincipal principal,
                                                            @Valid @RequestBody ReserveStockRequest request) {
        requirePermission(principal, "inventory:manage");
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.reserveStock(principal.tenantId(), request));
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<StockReservationResponse> confirm(@AuthenticationPrincipal InventoryPrincipal principal,
                                                             @PathVariable String reservationId,
                                                             @RequestParam(required = false) Long orderId) {
        requirePermission(principal, "inventory:manage");
        return ResponseEntity.ok(inventoryService.confirmReservation(principal.tenantId(), reservationId, orderId));
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ResponseEntity<Void> release(@AuthenticationPrincipal InventoryPrincipal principal,
                                        @PathVariable String reservationId) {
        requirePermission(principal, "inventory:manage");
        inventoryService.releaseReservation(principal.tenantId(), reservationId);
        return ResponseEntity.noContent().build();
    }

    private void requirePermission(InventoryPrincipal principal, String permission) {
        if (principal == null || !principal.hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assigned Inventory permission is required.");
        }
    }
}

package com.ecommerce.product.controller;

import com.ecommerce.product.dto.InventoryAnalyticsDto;
import com.ecommerce.product.security.CatalogPrincipal;
import com.ecommerce.product.service.InventoryAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products/admin/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product & Inventory Analytics", description = "Admin analytics for warehouse inventory, reorder thresholds, out-of-stock items, and stock valuation")
@SecurityRequirement(name = "bearerAuth")
public class InventoryAnalyticsController {

    private final InventoryAnalyticsService inventoryAnalyticsService;

    @Operation(summary = "Get Inventory Intelligence Summary", description = "Stock health, reorder breaches, category units and stock valuation "
            + "for the caller's tenant. Requires the analytics:read permission in the verified token.")
    @GetMapping("/inventory")
    public ResponseEntity<InventoryAnalyticsDto> getInventoryAnalytics(@AuthenticationPrincipal CatalogPrincipal actor) {
        if (actor == null || !actor.hasPermission("analytics:read")) {
            log.warn("Refused inventory analytics for user {} without analytics:read", actor == null ? null : actor.userId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The analytics:read permission is required.");
        }
        return ResponseEntity.ok(inventoryAnalyticsService.getInventoryAnalytics(actor.tenantId()));
    }
}

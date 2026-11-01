package com.ecommerce.product.controller;

import com.ecommerce.product.dto.InventoryAnalyticsDto;
import com.ecommerce.product.service.InventoryAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "Get Inventory Intelligence Summary", description = "Retrieves stock health, reorder breaches, category units, and warehouse asset valuation (Manager/Admin only).")
    @GetMapping("/inventory")
    public ResponseEntity<InventoryAnalyticsDto> getInventoryAnalytics(
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        assertManagerOrAdmin(userRole);
        return ResponseEntity.ok(inventoryAnalyticsService.getInventoryAnalytics());
    }

    private void assertManagerOrAdmin(String role) {
        if (role == null || (!role.toUpperCase().contains("ADMIN") && !role.toUpperCase().contains("MANAGER"))) {
            log.warn("Security Alert: Unauthorized read attempt to Product Analytics Console by role: {}", role);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Staff authorization (Manager or Admin) required.");
        }
    }
}

package com.ecommerce.stock.controller;

import com.ecommerce.stock.dto.*;
import com.ecommerce.stock.model.StockReservation;
import com.ecommerce.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stock & Reservations", description = "Endpoints for checking product availability, atomic stock holds, and restock alert subscriptions")
public class StockController {

    private final StockService inventoryService;

    @Value("${SERVICE_TO_SERVICE_TOKEN}")
    private String serviceToken;

    @Operation(summary = "Get Product Stock Status", description = "Retrieves authoritative stock availability for a catalog item.")
    @GetMapping("/{productId}")
    public ResponseEntity<StockItemDto> getInventory(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getInventory(productId));
    }

    @Operation(summary = "Batch Stock Availability Check", description = "Retrieves availability for multiple products for catalog presentation.")
    @GetMapping("/batch")
    public ResponseEntity<List<StockItemDto>> getBatchInventory(@RequestParam List<Long> productIds) {
        return ResponseEntity.ok(inventoryService.getBatchInventory(productIds));
    }

    @Operation(summary = "Batch Stock Availability Check (POST)", description = "Retrieves availability for multiple products for catalog presentation.")
    @PostMapping("/batch")
    public ResponseEntity<List<StockItemDto>> getBatchInventoryPost(@RequestBody List<Long> productIds) {
        return ResponseEntity.ok(inventoryService.getBatchInventory(productIds));
    }

    @Operation(summary = "Internal Stock Adjustment", description = "Endpoint for inter-service stock deduction/adjustment.")
    @PostMapping({"/adjustments", "/adjust"})
    public ResponseEntity<StockItemDto> adjustStockInternal(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-Service-Token", required = false) String suppliedServiceToken,
            @Valid @RequestBody StockAdjustmentRequest request) {
        boolean trustedService = suppliedServiceToken != null
                && MessageDigest.isEqual(suppliedServiceToken.getBytes(StandardCharsets.UTF_8), serviceToken.getBytes(StandardCharsets.UTF_8));
        boolean staff = role != null && (role.toUpperCase().contains("ADMIN") || role.toUpperCase().contains("MANAGER"));
        if (!trustedService && !staff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff role or valid service token required.");
        }
        try {
            return ResponseEntity.ok(inventoryService.adjustStock(request, email != null ? email : "SERVICE_INTERNAL"));
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concurrent stock update — please retry.");
        }
    }

    @Operation(summary = "Create Atomic Stock Reservation (Checkout Hold)", description = "Temporarily locks stock for an active checkout session with TTL expiration.")
    @PostMapping("/reservations")
    public ResponseEntity<StockReservationResponse> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.reserveStock(request));
    }

    @Operation(summary = "Confirm Stock Reservation", description = "Finalizes a stock hold upon successful order payment.")
    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<StockReservationResponse> confirmReservation(
            @PathVariable String reservationId,
            @RequestParam(required = false) Long orderId) {
        return ResponseEntity.ok(inventoryService.confirmReservation(reservationId, orderId));
    }

    @Operation(summary = "Release Stock Reservation (Compensation)", description = "Releases a stock hold if checkout fails or is abandoned.")
    @PostMapping("/reservations/{reservationId}/release")
    public ResponseEntity<Void> releaseReservation(@PathVariable String reservationId) {
        inventoryService.releaseReservation(reservationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Subscribe to Back-in-Stock Alerts", description = "Allows shoppers to receive an event notification when stock is replenished.")
    @PostMapping("/alerts/back-in-stock")
    public ResponseEntity<Void> subscribeBackInStock(@Valid @RequestBody StockAlertSubscriptionRequest request) {
        inventoryService.subscribeBackInStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

package com.ecommerce.inventory.ops;

import com.ecommerce.inventory.dto.ReserveStockRequest;
import com.ecommerce.inventory.dto.StockReservationResponse;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/** Service-to-service callbacks (order-service reservations, procurement receipts). Requires X-Service-Token and X-Tenant-Id; fails closed. */
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {
    private final InventoryService inventory;
    private final OpsService ops;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    private UUID tenant(String token, String tenantHeader) {
        if (serviceToken == null || serviceToken.isBlank() || token == null
                || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Valid service token required.");
        try { return UUID.fromString(tenantHeader); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id must be a tenant UUID."); }
    }

    @PostMapping("/reservations")
    public StockReservationResponse reserve(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                            @RequestBody ReserveStockRequest request) {
        return inventory.reserveStock(tenant(token, tenant), request);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public StockReservationResponse confirm(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                            @PathVariable String reservationId, @RequestParam(required = false) Long orderId) {
        return inventory.confirmReservation(tenant(token, tenant), reservationId, orderId);
    }

    @PostMapping("/reservations/{reservationId}/release")
    public Map<String, String> release(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                       @PathVariable String reservationId) {
        inventory.releaseReservation(tenant(token, tenant), reservationId);
        return Map.of("status", "RELEASED");
    }

    @PostMapping("/receipts")
    public Map<String, Object> receipt(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                       @RequestBody OpsService.ReceiptRequest request) {
        return ops.receive(tenant(token, tenant), request, "procurement-service");
    }

    @GetMapping("/forecast")
    public java.util.List<Map<String, Object>> forecast(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant) {
        return ops.forecast(tenant(token, tenant), 30);
    }

    @PostMapping("/incoming")
    public Map<String, String> incoming(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                        @RequestBody Map<String, Number> body) {
        ops.adjustIncoming(tenant(token, tenant), body.get("productId").longValue(), body.get("delta").intValue());
        return Map.of("status", "OK");
    }

    @PostMapping("/supplier-returns")
    public Map<String, Object> supplierReturn(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant,
                                              @RequestBody Map<String, Object> body) {
        return ops.supplierReturnOut(tenant(token, tenant), String.valueOf(body.get("sourceRef")), ((Number) body.get("warehouseId")).longValue(),
                ((Number) body.get("productId")).longValue(), ((Number) body.get("quantity")).intValue(), "procurement-service");
    }
}

package com.ecommerce.delivery.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal endpoint for order-service to trigger delivery record creation on order fulfilment (A-06).
 * Path is /internal/delivery/** so DeliveryTenantAuthenticationFilter does not intercept it (filter
 * only guards /api/delivery/**). Authenticated with the shared SERVICE_TO_SERVICE_TOKEN.
 */
@RestController
@RequestMapping("/internal/delivery")
public class InternalDeliveryAutoCreateController {
    private static final Logger log = LoggerFactory.getLogger(InternalDeliveryAutoCreateController.class);
    private static final Pattern POSTAL_CODE = Pattern.compile("\\b(\\d{5})(?:-\\d{4})?\\b");

    private final JdbcTemplate jdbc;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    @Value("${delivery.warehouse.name:Warehouse}")
    private String warehouseName;

    @Value("${delivery.warehouse.address:1 Commerce Way, Commerce City, CA 90040}")
    private String warehouseAddress;

    public InternalDeliveryAutoCreateController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/auto-create")
    @Transactional
    public ResponseEntity<Map<String, Object>> autoCreate(
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @RequestBody Map<String, Object> body) {
        authorize(token);
        long orderId = ((Number) body.get("orderId")).longValue();

        // Idempotency: one delivery per order
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM delivery WHERE order_id=?", Integer.class, orderId);
        if (existing != null && existing > 0) {
            return ResponseEntity.ok(Map.of("status", "already_exists", "orderId", orderId));
        }

        UUID tenantId = UUID.fromString(String.valueOf(body.get("tenantId")));
        String dropoffName   = safe(body.get("customerName"), "Customer");
        String dropoffAddress = safe(body.get("deliveryAddress"), "Address not provided");
        String businessType  = normalizeBusinessType(safe(body.get("businessType"), "RETAIL"));
        String postalCode    = extractPostalCode(dropoffAddress);
        int    itemCount     = body.get("itemCount") != null ? ((Number) body.get("itemCount")).intValue() : 1;
        Long   userId        = body.get("userId") != null ? ((Number) body.get("userId")).longValue() : null;
        BigDecimal total     = body.get("totalAmount") != null
                ? new BigDecimal(String.valueOf(body.get("totalAmount"))) : null;
        BigDecimal weightKg  = BigDecimal.valueOf(Math.max(1, itemCount)); // 1 kg per item estimate

        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO delivery(id,tenant_id,order_reference,recipient_user_id,business_type,
                        pickup_name,pickup_address,dropoff_name,dropoff_address,postal_code,
                        required_capability,weight_kg,item_count,scheduled_for,created_by,order_id,order_total)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    id, tenantId, "ORD-" + orderId, userId, businessType,
                    warehouseName, warehouseAddress,
                    dropoffName, dropoffAddress, postalCode,
                    null, weightKg, itemCount, null, 0L, orderId, total);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            return ResponseEntity.ok(Map.of("status", "already_exists", "orderId", orderId));
        }

        log.info("Auto-created delivery {} for order {} (tenant {})", id, orderId, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "created", "deliveryId", id.toString(), "orderId", orderId));
    }

    private void authorize(String supplied) {
        if (serviceToken == null || serviceToken.isBlank() || supplied == null
                || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8),
                                          supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid service credential");
        }
    }

    private static String safe(Object v, String fallback) {
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static String normalizeBusinessType(String t) {
        return "RESTAURANT".equalsIgnoreCase(t) ? "RESTAURANT" : "RETAIL";
    }

    private static String extractPostalCode(String address) {
        Matcher m = POSTAL_CODE.matcher(address == null ? "" : address);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : "00000";
    }
}

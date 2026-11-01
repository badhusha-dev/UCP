package com.ecommerce.order.rma;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/**
 * Callbacks for the two domains the Returns module depends on: Payment (refund confirmation) and Delivery (reverse-pickup progress).
 * They authenticate with the shared service token (fail closed when unset) and name the tenant explicitly. There is no administrator
 * button that marks a refund completed: this is the only way.
 */
@RestController
@RequestMapping("/internal/return-events")
public class ReturnEventsController {
    private final RefundService refunds;
    private final PickupService pickups;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}") private String serviceToken;

    public ReturnEventsController(RefundService refunds, PickupService pickups) { this.refunds = refunds; this.pickups = pickups; }

    public record RefundOutcome(String eventId, String attemptKey, String status, String paymentReference, BigDecimal amount, String failureReason) {}
    public record PickupProgress(String eventId, String pickupReference, String status, String partner, String trackingReference, String proofReference, String failureReason) {}

    private UUID tenant(String token, String tenant) {
        if (serviceToken == null || serviceToken.isBlank() || token == null || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid service token is required.");
        try { return UUID.fromString(tenant); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id must be a UUID."); }
    }

    @PostMapping("/refund")
    public Map<String, Object> refund(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant, @RequestBody RefundOutcome e) {
        UUID t = tenant(token, tenant); // authenticate before looking at the body
        if (e.attemptKey() == null || e.status() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "attemptKey and status are required.");
        if (!e.status().equals("SUCCEEDED") && !e.status().equals("FAILED")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be SUCCEEDED or FAILED.");
        return refunds.outcome(t, e.eventId(), e.attemptKey(), e.status().equals("SUCCEEDED"), e.paymentReference(), e.amount(), e.failureReason());
    }

    @PostMapping("/pickup")
    public Map<String, Object> pickup(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant, @RequestBody PickupProgress e) {
        UUID t = tenant(token, tenant);
        if (e.pickupReference() == null || e.status() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pickupReference and status are required.");
        return pickups.progress(t, e.eventId(), e.pickupReference(), e.status(), e.partner(), e.trackingReference(), e.proofReference(), e.failureReason());
    }
}

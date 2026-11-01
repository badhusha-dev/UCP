package com.ecommerce.order.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/**
 * Callbacks from the domains that own money. They authenticate with the shared service token (fail closed when unset) and name the
 * tenant explicitly. This is the only way an order becomes PAID or REFUNDED: there is no administrator button for either.
 * No Payment Service exists in this repository yet; these endpoints are the contract it (or Accounting) must call.
 */
@RestController
@RequestMapping("/internal/order-events")
public class InternalOrderEventsController {
    private final OrderAdminService admin;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}") private String serviceToken;

    public InternalOrderEventsController(OrderAdminService admin) { this.admin = admin; }

    public record PaymentEvent(String eventId, Long orderId, String status, String method, String reference) {}
    public record RefundEvent(String eventId, Long orderId, String status, String reference) {}

    private UUID tenant(String token, String tenant) {
        if (serviceToken == null || serviceToken.isBlank() || token == null || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid service token is required.");
        try { return UUID.fromString(tenant); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id must be a UUID."); }
    }

    @PostMapping("/payment")
    public Map<String, Object> payment(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant, @RequestBody PaymentEvent e) {
        UUID t = tenant(token, tenant); // authenticate before looking at the body
        if (e.orderId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required.");
        return admin.onPaymentEvent(t, e.eventId(), e.orderId(), e.status(), e.method(), e.reference());
    }

    @PostMapping("/refund")
    public Map<String, Object> refund(@RequestHeader(value = "X-Service-Token", required = false) String token, @RequestHeader(value = "X-Tenant-Id", required = false) String tenant, @RequestBody RefundEvent e) {
        UUID t = tenant(token, tenant);
        if (e.orderId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId is required.");
        return admin.onRefundEvent(t, e.eventId(), e.orderId(), e.status(), e.reference());
    }
}

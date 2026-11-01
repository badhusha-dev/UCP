package com.ecommerce.product.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service API for checkout. Not routed by the gateway; callers present the shared service token and the tenant.
 * It fails closed when no token is configured.
 */
@RestController
@RequestMapping("/internal/pricing")
public class InternalPricingController {
    private final QuoteService quotes;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    public InternalPricingController(QuoteService quotes) { this.quotes = quotes; }

    private UUID authorize(String token, String tenant) {
        if (serviceToken == null || serviceToken.isBlank() || token == null
                || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid service token is required.");
        }
        try { return UUID.fromString(tenant); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id must be a UUID."); }
    }

    @PostMapping("/quote")
    public Map<String, Object> quote(@RequestHeader(value = "X-Service-Token", required = false) String token,
                                     @RequestHeader(value = "X-Tenant-Id", required = false) String tenant, @RequestBody QuoteService.QuoteRequest request) {
        return quotes.quote(authorize(token, tenant), request);
    }

    @PostMapping("/redeem")
    public Map<String, Object> redeem(@RequestHeader(value = "X-Service-Token", required = false) String token,
                                      @RequestHeader(value = "X-Tenant-Id", required = false) String tenant, @RequestBody QuoteService.QuoteRequest request) {
        return quotes.redeem(authorize(token, tenant), request);
    }

    @PostMapping("/reverse/{orderRef}")
    public Map<String, Object> reverse(@RequestHeader(value = "X-Service-Token", required = false) String token,
                                       @RequestHeader(value = "X-Tenant-Id", required = false) String tenant, @PathVariable String orderRef) {
        return quotes.reverse(authorize(token, tenant), orderRef);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalid(IllegalArgumentException e) { return Map.of("message", e.getMessage()); }
}

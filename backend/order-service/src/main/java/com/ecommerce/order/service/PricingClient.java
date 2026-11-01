package com.ecommerce.order.service;

import com.ecommerce.order.dto.CartItemDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checkout-side client for the pricing module hosted in product-service. When enabled, prices and promotions are computed
 * and consumed on the backend; client-supplied prices are ignored. Disabled by default because order-service has no tenant
 * scoping yet, so a single tenant id is configured for the deployment.
 */
@Component
public class PricingClient {
    private final RestTemplate restTemplate;

    @Value("${pricing.integration.enabled:false}") private boolean enabled;
    @Value("${pricing.integration.tenant-id:}") private String tenantId;
    @Value("${pricing.integration.currency:USD}") private String currency;
    @Value("${services.product.url:http://localhost:8082}") private String productServiceUrl;
    @Value("${SERVICE_TO_SERVICE_TOKEN:local-dev-service-token-change-me}") private String serviceToken;

    public PricingClient(RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    /** The tenant orders are attributed to when the integration is on; null otherwise. */
    public java.util.UUID tenantUuid() {
        if (!enabled()) return null;
        try { return java.util.UUID.fromString(tenantId.trim()); } catch (IllegalArgumentException e) { return null; }
    }

    public boolean enabled() { return enabled && tenantId != null && !tenantId.isBlank(); }

    /** Consumes promotions for the order in the configured deployment tenant (legacy single-tenant checkout). */
    public Map<String, Object> redeem(String orderRef, Long userId, List<CartItemDto> items, String couponCode) {
        return redeem(tenantUuid(), orderRef, userId, items, couponCode);
    }

    /** Consumes promotions for the order in the given tenant and returns the server-computed lines, discount and total. Pricing
     *  refuses any product that does not belong to that tenant, so this also proves every line is the tenant's own product. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> redeem(java.util.UUID tenant, String orderRef, Long userId, List<CartItemDto> items, String couponCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("currency", currency);
        body.put("orderRef", orderRef);
        body.put("customer", Map.of("id", String.valueOf(userId)));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (CartItemDto item : items) lines.add(Map.of("productId", item.getProductId(), "quantity", item.getQuantity()));
        body.put("lines", lines);
        if (couponCode != null && !couponCode.isBlank()) body.put("couponCode", couponCode.trim());
        return post("/internal/pricing/redeem", body, tenant);
    }

    public void reverse(String orderRef) {
        reverse(tenantUuid(), orderRef);
    }

    public void reverse(java.util.UUID tenant, String orderRef) {
        post("/internal/pricing/reverse/" + orderRef, Map.of(), tenant);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> post(String path, Object body, java.util.UUID tenant) {
        if (tenant == null) throw new IllegalStateException("Pricing needs a tenant: none configured and none resolved from a storefront.");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Service-Token", serviceToken);
        headers.set("X-Tenant-Id", tenant.toString());
        return restTemplate.exchange(productServiceUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody();
    }
}

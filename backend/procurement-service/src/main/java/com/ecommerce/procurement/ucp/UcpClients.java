package com.ecommerce.procurement.ucp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Calls inventory-service through its service-token API (tenant passed explicitly). Never touches inventory tables. */
@Component
class InventoryClient {
    private final RestTemplate rest = new RestTemplate();
    @Value("${services.inventory.url:http://localhost:8085}") private String url;
    @Value("${SERVICE_TO_SERVICE_TOKEN:}") private String token;

    private HttpHeaders headers(UUID tenant) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Service-Token", token); h.set("X-Tenant-Id", tenant.toString()); h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    Map<String, Object> receipt(UUID tenant, Map<String, Object> body) { return post(tenant, "/internal/inventory/receipts", body); }
    Map<String, Object> incoming(UUID tenant, long productId, int delta) { return post(tenant, "/internal/inventory/incoming", Map.of("productId", productId, "delta", delta)); }
    Map<String, Object> supplierReturn(UUID tenant, Map<String, Object> body) { return post(tenant, "/internal/inventory/supplier-returns", body); }

    List<Map<String, Object>> forecast(UUID tenant) {
        return rest.exchange(url + "/internal/inventory/forecast", HttpMethod.GET, new HttpEntity<>(headers(tenant)), new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
    }

    private Map<String, Object> post(UUID tenant, String path, Map<String, Object> body) {
        return rest.exchange(url + path, HttpMethod.POST, new HttpEntity<>(body, headers(tenant)), new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }
}

@Component
class AccountingClient {
    private final RestTemplate rest = new RestTemplate();
    @Value("${services.accounting.url:http://localhost:8088}") private String url;
    @Value("${SERVICE_TO_SERVICE_TOKEN:}") private String token;

    /** Posts a supplier credit note through the accounting API; idempotent on idempotencyKey. */
    Map<String, Object> creditNote(Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Service-Token", token); h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url + "/internal/accounting/supplier-credit-notes", HttpMethod.POST, new HttpEntity<>(body, h), new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }
}

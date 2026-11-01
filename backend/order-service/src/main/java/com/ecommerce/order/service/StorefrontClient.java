package com.ecommerce.order.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Resolves the storefront a shopper is buying from to its tenant, store and type (product-service is the owner of storefronts).
 * The shopper names the public storefront slug; the tenant always comes from product-service, never from the request.
 */
@Component
public class StorefrontClient {
    private final RestTemplate restTemplate;

    @Value("${services.product.url:http://localhost:8082}") private String productServiceUrl;
    @Value("${SERVICE_TO_SERVICE_TOKEN}") private String serviceToken;

    public StorefrontClient(RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    public record Storefront(UUID tenantId, String storeId, String storefrontType, String slug) {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Storefront resolve(String slug) {
        if (slug == null || !slug.trim().matches("[A-Za-z0-9][A-Za-z0-9-]{0,62}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown storefront.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", serviceToken);
        try {
            Map body = restTemplate.exchange(productServiceUrl + "/internal/storefronts/" + slug.trim().toLowerCase(java.util.Locale.ROOT),
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            if (body == null || body.get("tenantId") == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Storefront lookup failed.");
            return new Storefront(UUID.fromString(String.valueOf(body.get("tenantId"))), String.valueOf(body.get("storeId")),
                    String.valueOf(body.get("storefrontType")), String.valueOf(body.get("slug")));
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown storefront.");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Storefront lookup is temporarily unavailable.");
        }
    }
}

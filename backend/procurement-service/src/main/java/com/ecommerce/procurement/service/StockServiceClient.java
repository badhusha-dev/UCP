package com.ecommerce.procurement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class StockServiceClient {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.stock.url:http://localhost:8090}")
    private String stockUrl;

    @Value("${SERVICE_TO_SERVICE_TOKEN}")
    private String serviceToken;

    public List<Map<String, Object>> lowStockItems() {
        return restTemplate.exchange(stockUrl + "/internal/stock/low-stock", HttpMethod.GET,
                new HttpEntity<>(headers()), new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
    }

    public Map<String, Object> receive(long productId, int quantity, BigDecimal unitCost, String referenceId, String reason) {
        Map<String, Object> body = Map.of("productId", productId, "quantity", quantity, "unitCost", unitCost,
                "referenceId", referenceId, "reason", reason);
        return restTemplate.exchange(stockUrl + "/internal/stock/receive", HttpMethod.POST,
                new HttpEntity<>(body, headers()), new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", serviceToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

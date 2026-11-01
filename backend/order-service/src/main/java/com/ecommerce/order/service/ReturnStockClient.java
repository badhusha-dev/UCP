package com.ecommerce.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReturnStockClient {
    private final RestTemplate restTemplate;

    @Value("${services.stock.url:http://localhost:8090}")
    private String stockServiceUrl;

    @Value("${SERVICE_TO_SERVICE_TOKEN:local-dev-service-token-change-me}")
    private String serviceToken;

    public void restock(Long productId, int quantity, String referenceId, String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Service-Token", serviceToken);
        Map<String, Object> body = Map.of("productId", productId, "quantity", quantity,
                "referenceId", referenceId, "reason", reason);
        restTemplate.postForEntity(stockServiceUrl + "/internal/stock/returns/restock",
                new HttpEntity<>(body, headers), Map.class);
    }
}

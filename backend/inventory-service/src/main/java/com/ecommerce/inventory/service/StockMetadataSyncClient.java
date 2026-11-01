package com.ecommerce.inventory.service;

import com.ecommerce.inventory.model.InventoryItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class StockMetadataSyncClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.stock.url:http://localhost:8090}")
    private String stockServiceUrl;

    @Value("${SERVICE_TO_SERVICE_TOKEN:local-dev-service-token-change-me}")
    private String serviceToken;

    public void sync(InventoryItem item) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Token", serviceToken);
            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", item.getProductId());
            payload.put("sku", item.getSku());
            payload.put("reorderThreshold", item.getReorderThreshold());
            payload.put("costPrice", item.getCostPrice());
            payload.put("warehouseLocation", item.getWarehouseLocation());
            restTemplate.exchange(
                    stockServiceUrl + "/internal/stock/metadata/" + item.getProductId(),
                    HttpMethod.PUT,
                    new HttpEntity<>(payload, headers),
                    Void.class);
        } catch (Exception exception) {
            log.warn("Stock metadata cache sync failed for product {}: {}", item.getProductId(), exception.getMessage());
        }
    }
}

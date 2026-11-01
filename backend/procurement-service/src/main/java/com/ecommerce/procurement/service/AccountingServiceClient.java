package com.ecommerce.procurement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class AccountingServiceClient {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.accounting.url:http://localhost:8088}")
    private String accountingUrl;

    @Value("${SERVICE_TO_SERVICE_TOKEN:local-dev-service-token-change-me}")
    private String serviceToken;

    public Map<String, Object> recordSupplierPayable(Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", serviceToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(accountingUrl + "/internal/accounting/supplier-payables", HttpMethod.POST,
                new HttpEntity<>(request, headers), new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }
}

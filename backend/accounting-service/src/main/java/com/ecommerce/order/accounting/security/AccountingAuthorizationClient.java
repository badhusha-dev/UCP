package com.ecommerce.order.accounting.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class AccountingAuthorizationClient {
    private final RestClient restClient = RestClient.builder().build();

    @Value("${identity.service.url:${IDENTITY_SERVICE_URL:http://localhost:8081}}")
    private String identityServiceUrl;

    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    /** Confirms with the identity service that the token's tenant membership and Accounting grant are still active. */
    public void requireCurrentGrant(String accessToken) {
        if (!StringUtils.hasText(serviceToken)) throw new AuthorizationUnavailableException();
        restClient.post()
                .uri(identityServiceUrl + "/api/users/internal/authorization/check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Service-Token", serviceToken)
                .retrieve()
                .toBodilessEntity();
    }

    public static class AuthorizationUnavailableException extends RuntimeException {}
}

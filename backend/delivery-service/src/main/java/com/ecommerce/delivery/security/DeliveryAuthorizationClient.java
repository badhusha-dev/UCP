package com.ecommerce.delivery.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class DeliveryAuthorizationClient {
    private final RestClient client = RestClient.builder().build();

    @Value("${identity.service.url:http://localhost:8081}")
    private String identityServiceUrl;
    @Value("${SERVICE_TO_SERVICE_TOKEN:}")
    private String serviceToken;

    public void requireCurrentGrant(String token) {
        if (!StringUtils.hasText(serviceToken)) throw new AuthorizationUnavailableException();
        client.post().uri(identityServiceUrl + "/api/users/internal/authorization/check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Service-Token", serviceToken).retrieve().toBodilessEntity();
    }

    public static class AuthorizationUnavailableException extends RuntimeException {}
}

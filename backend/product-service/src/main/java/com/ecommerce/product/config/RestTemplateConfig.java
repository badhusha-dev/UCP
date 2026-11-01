package com.ecommerce.product.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.web.client.RestTemplate;

/**
 * Creates a RestTemplate wired with Micrometer Observation support.
 * Outgoing calls from product-service to order-service carry
 * W3C trace context headers automatically.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ObservationRegistry observationRegistry) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setObservationRegistry(observationRegistry);
        restTemplate.setObservationConvention(new DefaultClientRequestObservationConvention());
        return restTemplate;
    }
}

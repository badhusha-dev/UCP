package com.ecommerce.order.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.web.client.RestTemplate;

/**
 * Creates a RestTemplate wired with Micrometer Observation support.
 * This causes outgoing HTTP calls from order-service to include
 * W3C trace context headers (traceparent / tracestate) that are
 * picked up by product-service's OTel instrumentation.
 *
 * Connect and read timeouts are bounded so a downstream service that accepts
 * a connection but never answers cannot hold a checkout thread indefinitely.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ObservationRegistry observationRegistry,
                                     @Value("${http.client.connect-timeout-ms:2000}") int connectTimeoutMs,
                                     @Value("${http.client.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.setObservationRegistry(observationRegistry);
        restTemplate.setObservationConvention(new DefaultClientRequestObservationConvention());
        return restTemplate;
    }
}

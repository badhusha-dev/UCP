package com.ecommerce.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("revocationWebClientBuilder")
    @LoadBalanced
    public WebClient.Builder revocationWebClientBuilder() {
        return WebClient.builder();
    }
}

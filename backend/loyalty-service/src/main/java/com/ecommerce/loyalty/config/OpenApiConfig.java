package com.ecommerce.loyalty.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI loyaltyServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Loyalty Service API")
                        .version("1.0.0")
                        .description("Loyalty points, tier management and redemption")
                        .contact(new Contact().name("UCP Platform").email("platform@ecommerce.com")));
    }
}

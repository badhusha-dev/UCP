package com.ecommerce.cart.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cartServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cart Service API")
                        .description("Persistent Carts, Guest Cart Merge, Cart Synchronization, Live Validation and Abandoned Cart Recovery")
                        .version("v2.0.0")
                        .contact(new Contact().name("Universal Commerce Engineering").email("support@universalcommerce.io"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")));
    }
}

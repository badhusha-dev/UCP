package com.ecommerce.stock.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UCP Stock Service API")
                        .description("Authoritative Inventory, Stock Levels, Atomic Reservations, Stock Movements, and Restock Alerts")
                        .version("2.0.0")
                        .contact(new Contact().name("UCP Architecture Team").email("architecture@ecommerce.com"))
                        .license(new License().name("Apache 2.0").url("https://spring.io")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .schemaRequirement("bearerAuth", new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}

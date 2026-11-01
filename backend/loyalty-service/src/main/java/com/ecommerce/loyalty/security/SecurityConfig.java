package com.ecommerce.loyalty.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LoyaltyTenantAuthFilter loyaltyTenantAuthFilter;

    public SecurityConfig(LoyaltyTenantAuthFilter loyaltyTenantAuthFilter) {
        this.loyaltyTenantAuthFilter = loyaltyTenantAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(loyaltyTenantAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Loyalty endpoints require a valid tenant JWT — the filter enforces this
                .requestMatchers("/api/loyalty/**").authenticated()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}

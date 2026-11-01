package com.ecommerce.crm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain crmSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .addFilterBefore(new StaffAccessFilter(), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll().anyRequest().denyAll())
                .build();
    }
}

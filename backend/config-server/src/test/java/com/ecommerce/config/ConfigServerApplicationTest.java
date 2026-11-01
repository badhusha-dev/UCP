package com.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConfigServerApplicationTest {

    @Test
    @DisplayName("Should successfully bootstrap Spring Cloud Config Server context")
    void contextLoads() {
    }
}

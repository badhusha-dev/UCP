package com.ecommerce.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class DiscoveryServerApplicationTest {

    @Test
    @DisplayName("Should successfully bootstrap Eureka Discovery Server context")
    void contextLoads() {
    }
}

package com.ecommerce.product;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/products should return list of products")
    void testGetAllProducts() throws Exception {
        Product p = new Product();
        p.setName("Wireless Mouse");
        p.setDescription("Ergonomic Bluetooth mouse");
        p.setPrice(34.99);
        p.setStockQuantity(50);
        productRepository.save(p);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Wireless Mouse"))
                .andExpect(jsonPath("$[0].price").value(34.99))
                .andExpect(jsonPath("$[0].stockQuantity").value(50));
    }

    @Test
    @DisplayName("GET /api/products/{id} should return single product")
    void testGetProductById() throws Exception {
        Product p = new Product();
        p.setName("USB-C Hub");
        p.setDescription("7-in-1 multi-port hub");
        p.setPrice(49.99);
        p.setStockQuantity(30);
        Product saved = productRepository.save(p);

        mockMvc.perform(get("/api/products/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.name").value("USB-C Hub"))
                .andExpect(jsonPath("$.stockQuantity").value(30));
    }
}

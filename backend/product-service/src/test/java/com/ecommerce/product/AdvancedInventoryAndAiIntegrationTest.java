package com.ecommerce.product;

import com.ecommerce.product.dto.AiChatRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdvancedInventoryAndAiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        Product product = new Product();
        product.setName("Mechanical Gaming Keyboard");
        product.setDescription("RGB backlit mechanical keyboard with tactile blue switches.");
        product.setPrice(129.99);
        product.setStockQuantity(50);
        testProduct = productRepository.save(product);
    }

    @Test
    @DisplayName("GET /api/products/export/csv should stream formatted CSV catalog data")
    void testExportProductsCsv() throws Exception {
        mockMvc.perform(get("/api/products/export/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().string(containsString("id,name,description,price,stockQuantity,category,sku")))
                .andExpect(content().string(containsString("Mechanical Gaming Keyboard")))
                .andExpect(content().string(containsString("129.99")));
    }

    @Test
    @DisplayName("POST /api/products/import/csv should parse, validate, and bulk import products")
    void testImportProductsCsv() throws Exception {
        String csvContent = "id,name,description,price,stockQuantity,category,sku\n"
                + "99,\"USB-C Hub 7-in-1\",\"Multiport adapter with HDMI and SD reader\",39.99,150,\"Accessories\",\"SKU-99\"\n"
                + "100,\"Invalid Negative Price\",\"Defective item\",-10.00,5,\"Accessories\",\"SKU-100\"\n";

        mockMvc.perform(post("/api/products/import/csv")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(csvContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.successfulImports").value(1))
                .andExpect(jsonPath("$.failedRows").value(1))
                .andExpect(jsonPath("$.errorMessages", hasSize(1)));

        // Verify USB-C Hub was created in repository
        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().contains("USB-C Hub"))).isTrue();
    }

    @Test
    @DisplayName("POST /api/products/ai/chat should provide verified catalog answers strictly from database")
    void testAiAssistantChat() throws Exception {
        AiChatRequest req = new AiChatRequest();
        req.setProductId(testProduct.getId());
        req.setQuestion("How many units are in stock and what is the price?");

        mockMvc.perform(post("/api/products/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verifiedAgainstDatabase").value(true))
                .andExpect(jsonPath("$.answer").value(containsString("50 units")))
                .andExpect(jsonPath("$.answer").value(containsString("Mechanical Gaming Keyboard")))
                .andExpect(jsonPath("$.sourceProducts", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/products/search/natural should parse NL criteria into structured filters")
    void testNaturalLanguageSearch() throws Exception {
        mockMvc.perform(get("/api/products/search/natural")
                        .param("query", "keyboard under $150 in stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxPrice").value(150.0))
                .andExpect(jsonPath("$.inStockOnly").value(true))
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.matchingProducts[0].name").value("Mechanical Gaming Keyboard"));
    }
}

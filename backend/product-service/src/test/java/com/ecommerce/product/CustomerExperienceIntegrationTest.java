package com.ecommerce.product;

import com.ecommerce.product.dto.CreateReviewRequest;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductCategory;
import com.ecommerce.product.model.ProductVariant;
import com.ecommerce.product.repository.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CustomerExperienceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private CustomerWishlistRepository wishlistRepository;

    @Autowired
    private ProductReviewRepository reviewRepository;

    @Autowired
    private RecentlyViewedRepository recentlyViewedRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private ProductCategory audioCategory;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        wishlistRepository.deleteAll();
        reviewRepository.deleteAll();
        recentlyViewedRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        audioCategory = categoryRepository.save(ProductCategory.builder()
                .name("Audio Gear")
                .slug("audio-gear")
                .description("Headphones & Speakers")
                .build());

        testProduct1 = productRepository.save(Product.builder()
                .name("Noise Canceling Earbuds")
                .description("True wireless ANC earbuds with 24h case")
                .price(129.99)
                .stockQuantity(45)
                .category(audioCategory)
                .rating(5.0)
                .reviewCount(0)
                .build());

        testProduct2 = productRepository.save(Product.builder()
                .name("Studio Monitor Speakers")
                .description("Near-field active studio monitors pair")
                .price(299.99)
                .stockQuantity(15)
                .category(audioCategory)
                .rating(4.5)
                .reviewCount(1)
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(testProduct1)
                .sku("EARBUDS-BLK")
                .name("Matte Black")
                .color("Black")
                .size("Standard")
                .priceAdjustment(0.0)
                .stockQuantity(25)
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(testProduct1)
                .sku("EARBUDS-WHT")
                .name("Glacier White")
                .color("White")
                .size("Standard")
                .priceAdjustment(10.0)
                .stockQuantity(20)
                .build());
    }

    @Test
    @DisplayName("GET /api/products/categories should return all categories")
    void testGetCategories() throws Exception {
        mockMvc.perform(get("/api/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Audio Gear"))
                .andExpect(jsonPath("$[0].slug").value("audio-gear"));
    }

    @Test
    @DisplayName("GET /api/products with search filters should return paginated matching products")
    void testSearchProducts() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("query", "Earbuds")
                        .param("minPrice", "100.0")
                        .param("maxPrice", "200.0")
                        .param("sortBy", "price_asc")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Noise Canceling Earbuds"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/products/{id}/variants should return variants with adjusted prices")
    void testGetProductVariants() throws Exception {
        mockMvc.perform(get("/api/products/" + testProduct1.getId() + "/variants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sku").value("EARBUDS-BLK"))
                .andExpect(jsonPath("$[1].sku").value("EARBUDS-WHT"))
                .andExpect(jsonPath("$[1].finalPrice").value(139.99));
    }

    @Test
    @DisplayName("Customer Wishlist: Add, Check, List, and Remove flow")
    void testWishlistWorkflow() throws Exception {
        Long userId = 42L;

        // 1. Add to wishlist
        mockMvc.perform(post("/api/products/wishlist/" + testProduct1.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.added").value(true));

        // 2. Check wishlist status
        mockMvc.perform(get("/api/products/wishlist/check/" + testProduct1.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inWishlist").value(true));

        // 3. List wishlist products
        mockMvc.perform(get("/api/products/wishlist")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(testProduct1.getId()))
                .andExpect(jsonPath("$[0].name").value("Noise Canceling Earbuds"));

        // 4. Remove from wishlist
        mockMvc.perform(delete("/api/products/wishlist/" + testProduct1.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(true));

        // 5. Verify removed
        mockMvc.perform(get("/api/products/wishlist/check/" + testProduct1.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inWishlist").value(false));
    }

    @Test
    @DisplayName("Product Reviews: Add review, recalculate product rating, and reject duplicate")
    void testProductReviewsFlow() throws Exception {
        Long userId = 77L;
        CreateReviewRequest request = CreateReviewRequest.builder()
                .rating(4)
                .comment("Great noise cancellation, but fit was slightly tight.")
                .build();

        // 1. Submit review
        mockMvc.perform(post("/api/products/" + testProduct1.getId() + "/reviews")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Email", "sam@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.userName").value("sam"));

        // 2. Query reviews
        mockMvc.perform(get("/api/products/" + testProduct1.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].rating").value(4))
                .andExpect(jsonPath("$[0].comment").value("Great noise cancellation, but fit was slightly tight."));

        // 3. Verify product updated with new aggregate rating
        Product updatedProduct = productRepository.findById(testProduct1.getId()).orElseThrow();
        assertThat(updatedProduct.getRating()).isEqualTo(4.0);
        assertThat(updatedProduct.getReviewCount()).isEqualTo(1);

        // 4. Reject duplicate review from same user
        mockMvc.perform(post("/api/products/" + testProduct1.getId() + "/reviews")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Email", "sam@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You have already reviewed this product."));
    }

    @Test
    @DisplayName("Recently Viewed: Record product view and retrieve recent history")
    void testRecentlyViewedFlow() throws Exception {
        Long userId = 99L;

        // Record views
        mockMvc.perform(post("/api/products/" + testProduct1.getId() + "/view")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/products/" + testProduct2.getId() + "/view")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        // Retrieve recently viewed
        mockMvc.perform(get("/api/products/recently-viewed")
                        .header("X-User-Id", userId.toString())
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(testProduct2.getId()))
                .andExpect(jsonPath("$[1].id").value(testProduct1.getId()));
    }

    @Test
    @DisplayName("GET /api/products/compare should return products side-by-side")
    void testCompareProducts() throws Exception {
        mockMvc.perform(get("/api/products/compare")
                        .param("ids", testProduct1.getId() + "," + testProduct2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }
}

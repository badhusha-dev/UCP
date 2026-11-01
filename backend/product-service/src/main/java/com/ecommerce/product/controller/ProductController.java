package com.ecommerce.product.controller;

import com.ecommerce.product.dto.*;
import com.ecommerce.product.model.*;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.storefront.PublicStorefrontService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product Catalog & Customer Experience", description = "Endpoints for viewing catalog items, product variants, wishlist, reviews, and categories")
public class ProductController {

    private final ProductService productService;
    private final PublicStorefrontService storefronts;

    @Operation(summary = "List & Search Products", description = "Retrieves all products or filters by query, category, price range, and sorting. With a storefront slug, only that storefront's products are returned.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Catalog products successfully retrieved"),
            @ApiResponse(responseCode = "404", description = "Unknown or inactive storefront")
    })
    @GetMapping
    public ResponseEntity<?> getProducts(
            @Parameter(description = "Public storefront slug; the storefront's tenant is resolved by the server", example = "acme") @RequestParam(required = false) String storefront,
            @Parameter(description = "Keyword search query (name or description)") @RequestParam(required = false) String query,
            @Parameter(description = "Category ID filter") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Minimum price in USD") @RequestParam(required = false) Double minPrice,
            @Parameter(description = "Maximum price in USD") @RequestParam(required = false) Double maxPrice,
            @Parameter(description = "Sort order: price_asc, price_desc, rating, name") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size) {

        // A storefront limits the catalog to its tenant, the same tenant checkout will price against
        UUID tenantId = storefront == null || storefront.isBlank() ? null : storefronts.tenantOf(storefront);

        // Backward compatibility: If no search filters or pagination requested, return full list
        if (query == null && categoryId == null && minPrice == null && maxPrice == null && sortBy == null && page == null && size == null) {
            return ResponseEntity.ok(tenantId == null ? productService.getAllProducts() : productService.getStorefrontProducts(tenantId));
        }

        Page<Product> productPage = productService.searchProducts(
                tenantId, query, categoryId, minPrice, maxPrice, sortBy,
                page != null ? page : 0,
                size != null ? size : 12
        );
        return ResponseEntity.ok(productPage);
    }

    @Operation(summary = "Get Product by ID", description = "Retrieves product details and real-time inventory count by product ID.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product found",
                    content = @Content(schema = @Schema(implementation = Product.class))),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(
            @Parameter(description = "Product unique ID", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @Operation(summary = "List Product Categories", description = "Retrieves all available product categories.")
    @GetMapping("/categories")
    public ResponseEntity<List<ProductCategory>> getCategories() {
        return ResponseEntity.ok(productService.getAllCategories());
    }

    @Operation(summary = "Get Category by Slug", description = "Retrieves category details by slug.")
    @GetMapping("/categories/{slug}")
    public ResponseEntity<ProductCategory> getCategoryBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getCategoryBySlug(slug));
    }

    @Operation(summary = "List Product Variants", description = "Retrieves color, size, and SKU variations for a product.")
    @GetMapping("/{id}/variants")
    public ResponseEntity<List<ProductVariantDto>> getProductVariants(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getVariantsForProduct(id));
    }

    // --- Customer Wishlist ---
    @Operation(summary = "Get Customer Wishlist", description = "Retrieves saved products for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/wishlist")
    public ResponseEntity<List<Product>> getWishlist(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        Long userId = Long.parseLong(userIdHeader);
        return ResponseEntity.ok(productService.getWishlist(userId));
    }

    @Operation(summary = "Add Product to Wishlist", description = "Saves a product to customer wishlist.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/wishlist/{productId}")
    public ResponseEntity<Map<String, Object>> addToWishlist(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long productId) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required to use wishlist"));
        }
        Long userId = Long.parseLong(userIdHeader);
        boolean added = productService.addToWishlist(userId, productId);
        return ResponseEntity.ok(Map.of("success", true, "added", added, "productId", productId));
    }

    @Operation(summary = "Remove Product from Wishlist", description = "Removes a product from customer wishlist.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @DeleteMapping("/wishlist/{productId}")
    public ResponseEntity<Map<String, Object>> removeFromWishlist(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long productId) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        Long userId = Long.parseLong(userIdHeader);
        productService.removeFromWishlist(userId, productId);
        return ResponseEntity.ok(Map.of("success", true, "removed", true, "productId", productId));
    }

    @Operation(summary = "Check Wishlist Status", description = "Checks whether a product is in customer wishlist.")
    @GetMapping("/wishlist/check/{productId}")
    public ResponseEntity<Map<String, Boolean>> checkWishlist(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long productId) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("inWishlist", false));
        }
        Long userId = Long.parseLong(userIdHeader);
        return ResponseEntity.ok(Map.of("inWishlist", productService.isInWishlist(userId, productId)));
    }

    // --- Product Reviews ---
    @Operation(summary = "Get Product Reviews", description = "Retrieves customer reviews and ratings for a product.")
    @GetMapping("/{id}/reviews")
    public ResponseEntity<List<ProductReviewDto>> getProductReviews(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getReviewsForProduct(id));
    }

    @Operation(summary = "Add Product Review", description = "Submits a review and star rating. Checks purchase verification automatically.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/{id}/reviews")
    public ResponseEntity<?> addProductReview(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Email", required = false) String userEmailHeader,
            @PathVariable Long id,
            @Valid @RequestBody CreateReviewRequest request) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required to review products"));
        }
        Long userId = Long.parseLong(userIdHeader);
        String userName = userEmailHeader != null ? userEmailHeader.split("@")[0] : "Verified Customer";
        try {
            ProductReviewDto review = productService.addReview(userId, userName, id, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(review);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Recently Viewed ---
    @Operation(summary = "Record Product View", description = "Records a product view for personalized history.")
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> recordProductView(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable Long id) {
        if (userIdHeader != null && !userIdHeader.trim().isEmpty()) {
            productService.recordProductView(Long.parseLong(userIdHeader), id);
        }
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get Recently Viewed Products", description = "Retrieves recently viewed items for the user.")
    @GetMapping("/recently-viewed")
    public ResponseEntity<List<Product>> getRecentlyViewed(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "6") int limit) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        Long userId = Long.parseLong(userIdHeader);
        return ResponseEntity.ok(productService.getRecentlyViewed(userId, limit));
    }

    // --- Product Comparison ---
    @Operation(summary = "Compare Products", description = "Retrieves full specifications for multiple products to display in a side-by-side comparison.")
    @GetMapping("/compare")
    public ResponseEntity<List<Product>> compareProducts(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(productService.compareProducts(ids));
    }

    // --- Resilience Demo (Existing) ---
    @Operation(summary = "Get System Orders (Inter-Service Demo)",
            description = "Queries Order Service via Resilience4j Circuit Breaker demonstration.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/system-orders")
    public ResponseEntity<List<Object>> getSystemOrders() {
        return ResponseEntity.ok(productService.getSystemOrders());
    }

    // Stock quantities, holds, and restock alerts are owned by Stock Service.

    // ==========================================
    // BULK CATALOG CSV EXPORT / IMPORT
    // ==========================================

    @Operation(summary = "Export Catalog to CSV", description = "Generates and downloads a CSV export of all products and inventory.")
    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv() {
        String csvData = productService.exportProductsCsv();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=catalog_products.csv")
                .body(csvData);
    }

    @Operation(summary = "Import Catalog from CSV", description = "Uploads and imports products in bulk with schema validation and dry-run mode.")
    @PostMapping(value = "/import/csv", consumes = "text/plain")
    public ResponseEntity<CsvImportResultDto> importCsv(
            @RequestBody String csvContent,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(productService.importProductsCsv(csvContent, dryRun));
    }

    // ==========================================
    // AI SHOPPING ASSISTANT & ZERO-HALLUCINATION RAG
    // ==========================================

    @Operation(summary = "AI Catalog Assistant Q&A", description = "RAG chat assistant providing verified answers strictly grounded in actual catalog database data.")
    @PostMapping("/ai/chat")
    public ResponseEntity<AiChatResponse> chatWithAi(@Valid @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(productService.chatWithAiAssistant(request));
    }

    @Operation(summary = "Personalized Product Recommendations", description = "Provides affinity-based and collaborative recommendations based on user and product context.")
    @GetMapping("/ai/recommendations")
    public ResponseEntity<List<Product>> getRecommendations(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long productId) {
        return ResponseEntity.ok(productService.getPersonalizedRecommendations(userId, productId));
    }

    @Operation(summary = "Natural Language Product Search", description = "Parses conversational expressions (e.g. 'keyboard under $100 in stock') into structured queries.")
    @GetMapping("/search/natural")
    public ResponseEntity<NaturalLanguageSearchResponse> searchNatural(@RequestParam String query) {
        return ResponseEntity.ok(productService.parseAndSearchNaturalLanguage(query));
    }
}

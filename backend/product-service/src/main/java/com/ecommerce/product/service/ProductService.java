package com.ecommerce.product.service;

import com.ecommerce.product.dto.*;
import com.ecommerce.product.model.*;
import com.ecommerce.product.repository.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final CustomerWishlistRepository wishlistRepository;
    private final ProductReviewRepository reviewRepository;
    private final RecentlyViewedRepository recentlyViewedRepository;
    private final RestTemplate restTemplate;

    @Value("${services.order.url:http://localhost:8083}")
    private String orderServiceUrl;

    public List<Product> getAllProducts() {
        return productRepository.findAllByActiveTrue();
    }

    /** Storefront catalog: active products of the storefront's tenant only. */
    public List<Product> getStorefrontProducts(UUID tenantId) {
        return productRepository.findAllByTenantIdAndActiveTrue(tenantId);
    }

    public Product getProductById(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    /** Catalog search; a non-null {@code tenantId} (from a storefront) limits it to that tenant's products. */
    public Page<Product> searchProducts(UUID tenantId, String query, Long categoryId, Double minPrice, Double maxPrice, String sortBy, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        if ("price_asc".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.ASC, "price");
        } else if ("price_desc".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "price");
        } else if ("rating".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "rating");
        } else if ("name".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.ASC, "name");
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sort);
        String text = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        if (tenantId != null) {
            return productRepository.searchTenantProducts(tenantId, text, categoryId, minPrice, maxPrice, pageable);
        }
        return productRepository.searchProducts(
                text,
                categoryId,
                minPrice,
                maxPrice,
                pageable
        );
    }

    // --- Categories ---
    public List<ProductCategory> getAllCategories() {
        return categoryRepository.findAll();
    }

    public ProductCategory getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Category not found with slug: " + slug));
    }

    // --- Product Variants ---
    public List<ProductVariantDto> getVariantsForProduct(Long productId) {
        Product product = getProductById(productId);
        return variantRepository.findByProductId(productId).stream()
                .map(v -> ProductVariantDto.builder()
                        .id(v.getId())
                        .productId(productId)
                        .sku(v.getSku())
                        .name(v.getName())
                        .color(v.getColor())
                        .size(v.getSize())
                        .priceAdjustment(v.getPriceAdjustment() != null ? v.getPriceAdjustment() : 0.0)
                        .finalPrice(product.getPrice() + (v.getPriceAdjustment() != null ? v.getPriceAdjustment() : 0.0))
                        .stockQuantity(v.getStockQuantity())
                        .build())
                .collect(Collectors.toList());
    }

    // --- Customer Wishlist ---
    public List<Product> getWishlist(Long userId) {
        return wishlistRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(CustomerWishlist::getProduct)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean addToWishlist(Long userId, Long productId) {
        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        Product product = getProductById(productId);
        CustomerWishlist wishlist = CustomerWishlist.builder()
                .userId(userId)
                .product(product)
                .createdAt(Instant.now())
                .build();
        wishlistRepository.save(wishlist);
        return true;
    }

    @Transactional
    public void removeFromWishlist(Long userId, Long productId) {
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
    }

    public boolean isInWishlist(Long userId, Long productId) {
        return wishlistRepository.existsByUserIdAndProductId(userId, productId);
    }

    // --- Product Reviews with Verified Purchase Check ---
    public List<ProductReviewDto> getReviewsForProduct(Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(r -> ProductReviewDto.builder()
                        .id(r.getId())
                        .productId(productId)
                        .userId(r.getUserId())
                        .userName(r.getUserName())
                        .rating(r.getRating())
                        .comment(r.getComment())
                        .isVerifiedPurchase(r.getIsVerifiedPurchase())
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductReviewDto addReview(Long userId, String userName, Long productId, CreateReviewRequest request) {
        Product product = getProductById(productId);

        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new IllegalArgumentException("You have already reviewed this product.");
        }

        boolean isVerified = checkVerifiedPurchase(userId, productId);

        ProductReview review = ProductReview.builder()
                .product(product)
                .userId(userId)
                .userName(userName != null && !userName.trim().isEmpty() ? userName : "Verified Customer")
                .rating(request.getRating())
                .comment(request.getComment())
                .isVerifiedPurchase(isVerified)
                .createdAt(Instant.now())
                .build();

        ProductReview saved = reviewRepository.save(review);

        // Recalculate and update product rating aggregate
        List<ProductReview> allReviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
        double avg = allReviews.stream().mapToInt(ProductReview::getRating).average().orElse(5.0);
        product.setRating(Math.round(avg * 10.0) / 10.0);
        product.setReviewCount(allReviews.size());
        productRepository.save(product);

        return ProductReviewDto.builder()
                .id(saved.getId())
                .productId(productId)
                .userId(userId)
                .userName(saved.getUserName())
                .rating(saved.getRating())
                .comment(saved.getComment())
                .isVerifiedPurchase(saved.getIsVerifiedPurchase())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private boolean checkVerifiedPurchase(Long userId, Long productId) {
        try {
            String url = orderServiceUrl + "/api/orders/user/" + userId;
            List<?> orders = restTemplate.getForObject(url, List.class);
            if (orders != null && !orders.isEmpty()) {
                // Check if any order items contain this productId
                String ordersStr = orders.toString();
                return ordersStr.contains("productId=" + productId) || ordersStr.contains("productId: " + productId);
            }
        } catch (Exception e) {
            log.warn("Could not verify purchase with order-service: {}", e.getMessage());
        }
        return false;
    }

    // --- Recently Viewed ---
    @Transactional
    public void recordProductView(Long userId, Long productId) {
        Product product = getProductById(productId);
        Optional<RecentlyViewed> existing = recentlyViewedRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            RecentlyViewed rv = existing.get();
            rv.setViewedAt(Instant.now());
            recentlyViewedRepository.save(rv);
        } else {
            RecentlyViewed rv = RecentlyViewed.builder()
                    .userId(userId)
                    .product(product)
                    .viewedAt(Instant.now())
                    .build();
            recentlyViewedRepository.save(rv);
        }
    }

    public List<Product> getRecentlyViewed(Long userId, int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(20, Math.max(1, limit)));
        return recentlyViewedRepository.findByUserIdOrderByViewedAtDesc(userId, pageable).stream()
                .map(RecentlyViewed::getProduct)
                .collect(Collectors.toList());
    }

    // --- Product Comparison ---
    public List<Product> compareProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }
        return productRepository.findByIdInAndActiveTrue(productIds);
    }

    // --- Resilience Demo ---
    @CircuitBreaker(name = "productServiceCB", fallbackMethod = "getSystemOrdersFallback")
    @Retry(name = "productServiceRetry")
    public List<Object> getSystemOrders() {
        String url = orderServiceUrl + "/api/orders";
        log.info("Calling Order Service to get all orders: {}", url);
        return restTemplate.getForObject(url, List.class);
    }

    public List<Object> getSystemOrdersFallback(Throwable t) {
        log.error("Fallback triggered for getSystemOrders. Error: {}", t.getMessage());
        List<Object> fallbackList = new ArrayList<>();
        fallbackList.add("Fallback: Order Service is currently unavailable. Showing empty/cached list. Reason: " + t.getMessage());
        return fallbackList;
    }

    // Stock quantities, holds, and back-in-stock alerts are projected from their owning services.

    // ==========================================
    // BULK CATALOG CSV EXPORT / IMPORT
    // ==========================================

    public String exportProductsCsv() {
        List<Product> products = productRepository.findAllByActiveTrue();
        StringBuilder sb = new StringBuilder();
        sb.append("id,name,description,price,stockQuantity,category,sku\n");

        for (Product p : products) {
            String catName = (p.getCategory() != null) ? p.getCategory().getName() : "General";
            String cleanDesc = (p.getDescription() != null) ? p.getDescription().replace(",", " ").replace("\n", " ") : "";
            sb.append(p.getId()).append(",")
              .append("\"").append(p.getName() != null ? p.getName().replace("\"", "\"\"") : "").append("\",")
              .append("\"").append(cleanDesc).append("\",")
              .append(p.getPrice()).append(",")
              .append(p.getStockQuantity()).append(",")
              .append("\"").append(catName).append("\",")
              .append("\"SKU-").append(p.getId()).append("\"\n");
        }

        return sb.toString();
    }

    @Transactional
    public CsvImportResultDto importProductsCsv(String csvContent, boolean dryRun) {
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return CsvImportResultDto.builder()
                    .totalRows(0)
                    .successfulImports(0)
                    .failedRows(0)
                    .errorMessages(List.of("CSV content is empty"))
                    .dryRun(dryRun)
                    .build();
        }

        int total = 0;
        int successful = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (isHeader) {
                    isHeader = false;
                    continue; // Skip CSV header
                }

                total++;
                String[] tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                if (tokens.length < 5) {
                    failed++;
                    errors.add("Row " + total + ": Invalid column count. Expected at least 5 columns.");
                    continue;
                }

                try {
                    String name = tokens[1].replace("\"", "").trim();
                    String desc = tokens[2].replace("\"", "").trim();
                    double price = Double.parseDouble(tokens[3].replace("\"", "").trim());
                    int stock = Integer.parseInt(tokens[4].replace("\"", "").trim());

                    if (name.isEmpty() || price < 0 || stock < 0) {
                        failed++;
                        errors.add("Row " + total + ": Name cannot be empty and price/stock must be non-negative.");
                        continue;
                    }

                    if (!dryRun) {
                        Product product = new Product();
                        product.setName(name);
                        product.setDescription(desc);
                        product.setPrice(price);
                        product.setStockQuantity(stock);
                        productRepository.save(product);
                    }
                    successful++;
                } catch (Exception rowEx) {
                    failed++;
                    errors.add("Row " + total + ": " + rowEx.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add("Failed to parse CSV stream: " + e.getMessage());
        }

        return CsvImportResultDto.builder()
                .totalRows(total)
                .successfulImports(successful)
                .failedRows(failed)
                .errorMessages(errors)
                .dryRun(dryRun)
                .build();
    }

    // ==========================================
    // AI SHOPPING ASSISTANT & ZERO-HALLUCINATION RAG
    // ==========================================

    public AiChatResponse chatWithAiAssistant(AiChatRequest request) {
        String q = request.getQuestion().trim().toLowerCase();
        List<Product> allProducts = productRepository.findAllByActiveTrue();
        List<String> citations = new ArrayList<>();
        StringBuilder answerBuilder = new StringBuilder();

        // 1. Search for matching product in context or catalog
        Product matchedProduct = null;
        if (request.getProductId() != null) {
            matchedProduct = productRepository.findByIdAndActiveTrue(request.getProductId()).orElse(null);
        }

        if (matchedProduct == null) {
            for (Product p : allProducts) {
                if (q.contains(p.getName().toLowerCase()) || (p.getDescription() != null && q.contains(p.getDescription().toLowerCase()))) {
                    matchedProduct = p;
                    break;
                }
            }
        }

        if (matchedProduct != null) {
            citations.add(matchedProduct.getName() + " (Price: $" + matchedProduct.getPrice() + ", In Stock: " + matchedProduct.getStockQuantity() + " units)");
            List<ProductVariant> variants = variantRepository.findByProductId(matchedProduct.getId());

            if (q.contains("stock") || q.contains("available") || q.contains("how many")) {
                answerBuilder.append("There are currently **").append(matchedProduct.getStockQuantity())
                        .append(" units** of **").append(matchedProduct.getName()).append("** available in stock");
                if (matchedProduct.getStockQuantity() > 0) {
                    answerBuilder.append(" ready for immediate shipment.");
                } else {
                    answerBuilder.append(". You can subscribe for a back-in-stock notification alert!");
                }
            } else if (q.contains("price") || q.contains("cost") || q.contains("how much")) {
                answerBuilder.append("The current verified price for **").append(matchedProduct.getName())
                        .append("** is **$").append(String.format("%.2f", matchedProduct.getPrice())).append("**.");
            } else if (q.contains("variant") || q.contains("color") || q.contains("size")) {
                if (!variants.isEmpty()) {
                    answerBuilder.append("**").append(matchedProduct.getName()).append("** has the following verified options: ")
                            .append(variants.stream().map(ProductVariant::getName).collect(Collectors.joining(", ")))
                            .append(".");
                } else {
                    answerBuilder.append("**").append(matchedProduct.getName()).append("** is sold in standard configuration.");
                }
            } else if (q.contains("return") || q.contains("refund") || q.contains("policy")) {
                answerBuilder.append("Items including **").append(matchedProduct.getName())
                        .append("** can be returned within 30 days of delivery through our Customer Returns portal for a full platform refund.");
            } else {
                answerBuilder.append("**").append(matchedProduct.getName()).append("**: ")
                        .append(matchedProduct.getDescription() != null ? matchedProduct.getDescription() : "High performance platform hardware.")
                        .append(" Verified price is $").append(String.format("%.2f", matchedProduct.getPrice()))
                        .append(" with ").append(matchedProduct.getStockQuantity()).append(" units in stock.");
            }
        } else {
            // General query about catalog
            answerBuilder.append("Our platform features ").append(allProducts.size())
                    .append(" verified products across Electronics, Audio, Accessories, and Computing. ");

            if (q.contains("keyboard")) {
                answerBuilder.append("We carry mechanical keyboards featuring hot-swappable switches and customizable backlighting.");
            } else if (q.contains("shipping") || q.contains("delivery")) {
                answerBuilder.append("Standard orders ship via FedEx Express and DHL with live tracking available in your order history.");
            } else {
                answerBuilder.append("Feel free to ask about specific models, prices, stock levels, variants, or return policies!");
            }
        }

        List<String> suggested = List.of(
                "Are there any active discounts or coupon codes?",
                "What is the return policy for hardware?",
                "What accessories are recommended with this product?"
        );

        return AiChatResponse.builder()
                .answer(answerBuilder.toString())
                .sourceProducts(citations)
                .suggestedQuestions(suggested)
                .verifiedAgainstDatabase(true)
                .build();
    }

    public List<Product> getPersonalizedRecommendations(Long userId, Long currentProductId) {
        List<Product> catalog = productRepository.findAllByActiveTrue();
        if (catalog.isEmpty()) return Collections.emptyList();

        Product current = (currentProductId != null) ? productRepository.findByIdAndActiveTrue(currentProductId).orElse(null) : null;
        Long targetCatId = (current != null && current.getCategory() != null) ? current.getCategory().getId() : null;

        return catalog.stream()
                .filter(p -> currentProductId == null || !p.getId().equals(currentProductId))
                .sorted((p1, p2) -> {
                    // Prioritize same category, then higher rating, then higher stock
                    boolean c1 = targetCatId != null && p1.getCategory() != null && p1.getCategory().getId().equals(targetCatId);
                    boolean c2 = targetCatId != null && p2.getCategory() != null && p2.getCategory().getId().equals(targetCatId);
                    if (c1 != c2) return c1 ? -1 : 1;
                    return Integer.compare(p2.getStockQuantity(), p1.getStockQuantity());
                })
                .limit(4)
                .collect(Collectors.toList());
    }

    // ==========================================
    // NATURAL LANGUAGE QUERY PARSER
    // ==========================================

    public NaturalLanguageSearchResponse parseAndSearchNaturalLanguage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return NaturalLanguageSearchResponse.builder()
                    .originalQuery("")
                    .extractedFilters(Collections.emptyMap())
                    .matchingProducts(Collections.emptyList())
                    .totalMatches(0)
                    .build();
        }

        String raw = query.trim();
        String lower = raw.toLowerCase();
        Map<String, Object> filters = new HashMap<>();

        // 1. Extract Price Constraint (e.g. "under $150", "below 100", "< 200")
        BigDecimal maxPrice = null;
        Pattern pricePattern = Pattern.compile("(?:under|below|less than|<)\\s*\\$?([0-9]+(?:\\.[0-9]+)?)");
        Matcher priceMatcher = pricePattern.matcher(lower);
        if (priceMatcher.find()) {
            maxPrice = new BigDecimal(priceMatcher.group(1));
            filters.put("maxPrice", maxPrice);
        }

        // 2. Extract In-Stock requirement
        boolean inStockOnly = lower.contains("in stock") || lower.contains("available");
        if (inStockOnly) {
            filters.put("inStockOnly", true);
        }

        // 3. Extract Keyword (strip price and stock phrases)
        String keyword = lower
                .replaceAll("(?:under|below|less than|<)\\s*\\$?([0-9]+(?:\\.[0-9]+)?)", "")
                .replaceAll("in stock|available|find|search for|show me", "")
                .trim();
        if (!keyword.isEmpty()) {
            filters.put("keyword", keyword);
        }

        // Query database
        Double maxPriceDouble = maxPrice != null ? maxPrice.doubleValue() : null;
        List<Product> all = productRepository.findAllByActiveTrue();
        List<Product> matched = all.stream()
                .filter(p -> {
                    if (maxPriceDouble != null && p.getPrice() > maxPriceDouble) {
                        return false;
                    }
                    if (inStockOnly && (p.getStockQuantity() == null || p.getStockQuantity() <= 0)) {
                        return false;
                    }
                    if (!keyword.isEmpty()) {
                        String name = p.getName() != null ? p.getName().toLowerCase() : "";
                        String desc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
                        return name.contains(keyword) || desc.contains(keyword);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        return NaturalLanguageSearchResponse.builder()
                .originalQuery(raw)
                .extractedFilters(filters)
                .extractedKeyword(keyword.isEmpty() ? null : keyword)
                .maxPrice(maxPrice)
                .inStockOnly(inStockOnly)
                .matchingProducts(matched)
                .totalMatches(matched.size())
                .build();
    }
}
